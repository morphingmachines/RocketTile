package ce

import chisel3._
import freechips.rocketchip.devices.tilelink.{BootROMParams, CLINT, CLINTParams, TLROM}
import freechips.rocketchip.diplomacy.BufferParams
import freechips.rocketchip.interrupts.{IntSinkNode, IntSinkParameters, IntSinkPortParameters}
import freechips.rocketchip.subsystem.CacheBlockBytes
import freechips.rocketchip.tile.TileKey
import freechips.rocketchip.tilelink.{
  EarlyAck,
  TLBroadcast,
  TLBuffer,
  TLCacheCork,
  TLClientParameters,
  TLFilter,
  TLFragmenter,
  TLIdentityNode,
  TLWidthWidget,
  TLXbar,
}
import freechips.rocketchip.util.SystemFileName
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.{LazyModule, LazyModuleImp}
import sifive.blocks.inclusivecache.{
  CacheParameters,
  InclusiveCache,
  InclusiveCacheMicroParameters,
  InclusiveCachePortParameters,
}
import testchipip.tsi._

class Uncore(implicit p: Parameters) extends LazyModule {
  val fileName       = SystemFileName(p(ce.BootROMFile))
  val bootrom_params = new BootROMParams(contentFileName = fileName)
  import java.nio.file.{Files, Paths}
  val rom_contents = Files.readAllBytes(Paths.get(bootrom_params.contentFileName.fileName))
  val tlrom = LazyModule(
    new TLROM(
      base = bootrom_params.address,
      size = bootrom_params.size,
      contentsDelayed = rom_contents.toIndexedSeq,
      beatBytes = p(CacheBlockBytes),
    ),
  )

  val ceXLen  = p(TileKey).core.xLen
  val clint   = LazyModule(new CLINT(params = CLINTParams(), beatBytes = ceXLen / 8))
  val intSink = IntSinkNode(Seq(IntSinkPortParameters(Seq(IntSinkParameters()))))

  def skipMMIO(x: TLClientParameters) = {
    val dcacheMMIO =
      x.requestFifo &&
        x.sourceId.start % 2 == 1 &&
        x.nodePath.last.name == "dcache.node"
    if (dcacheMMIO) None else Some(x)
  }
  val filter = LazyModule(new TLFilter(cfilter = skipMMIO)) // MMIO request need not go through coherence manager

  val mbus   = LazyModule(new TLXbar)
  val tsi2tl = LazyModule(new TSIToTileLink)

  val memoryNode = new TLIdentityNode

  clint.node := TLBuffer(BufferParams(1, false, false)) := TLFragmenter(
    ceXLen / 8,
    p(CacheBlockBytes),
    true,
    EarlyAck.AllPuts,
    true,
  )           := TLWidthWidget(p(CacheBlockBytes))       := mbus.node
  tlrom.node  := TLBuffer(BufferParams(1, false, false)) := mbus.node
  filter.node := mbus.node
  mbus.node   := tsi2tl.node

  intSink := clint.intnode

  lazy val module = new UncoreImp(this)
}

class UncoreImp(outer: Uncore) extends LazyModuleImp(outer) {
  val io = IO(new Bundle {
    val tsi   = new TSIIO
    val state = Output(UInt())
    val msip  = Output(Bool())
  })
  io.tsi <> outer.tsi2tl.module.io.tsi
  io.state                      := outer.tsi2tl.module.io.state
  io.msip                       := outer.intSink.in(0)._1(0)
  outer.clint.module.io.rtcTick := false.B
}

trait HasNoL2Cache { this: Uncore =>
  val tlBroadcast = LazyModule(new TLBroadcast(lineBytes = p(CacheBlockBytes), numTrackers = 2))
  memoryNode := tlBroadcast.node := filter.node
}

trait HasL2Cache { this: Uncore =>
  val l2CacheParams = CacheParameters(
    level = 2,
    ways = 2,
    sets = 64,
    blockBytes = p(CacheBlockBytes),
    beatBytes = p(CacheBlockBytes),
    hintsSkipProbe = false,
  )
  val l2MicroParams = InclusiveCacheMicroParameters(
    writeBytes = 4,
    portFactor = 2,
    memCycles = 8,
    innerBuf = InclusiveCachePortParameters.fullC,
    outerBuf = InclusiveCachePortParameters.full,
  )
  val l2cache = LazyModule(new InclusiveCache(l2CacheParams, l2MicroParams))
  val cork    = LazyModule(new TLCacheCork)

  memoryNode := cork.node := l2cache.node := filter.node
}
