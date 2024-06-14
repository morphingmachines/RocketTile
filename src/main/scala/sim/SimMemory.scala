package ce.sim

import chisel3._
import freechips.rocketchip.devices.tilelink.{BootROMParams, CLINT, CLINTParams, TLROM}
import freechips.rocketchip.diplomacy.{AddressSet, BufferParams, DisableMonitors, LazyModule, LazyModuleImp}
import freechips.rocketchip.interrupts.{IntSinkNode, IntSinkParameters, IntSinkPortParameters}
import freechips.rocketchip.subsystem.CacheBlockBytes
import freechips.rocketchip.tile.XLen
import freechips.rocketchip.tilelink.{EarlyAck, TLBuffer, TLCacheCork, TLFragmenter, TLRAM, TLWidthWidget, TLXbar}
import org.chipsalliance.cde.config.Parameters
import sifive.blocks.inclusivecache.{
  CacheParameters,
  InclusiveCache,
  InclusiveCacheMicroParameters,
  InclusiveCachePortParameters,
}
import testchipip.tsi._

class SimMemory(implicit p: Parameters) extends LazyModule {
  println(s"cacheBlockBytes:${p(CacheBlockBytes)}")

  val bootrom_params = new BootROMParams(contentFileName = p(ce.BootROMFile))
  import java.nio.file.{Files, Paths}
  val rom_contents = Files.readAllBytes(Paths.get(bootrom_params.contentFileName))
  val tlrom = LazyModule(
    new TLROM(
      base = bootrom_params.address,
      size = bootrom_params.size,
      contentsDelayed = rom_contents.toIndexedSeq,
      beatBytes = p(CacheBlockBytes),
    ),
  )

  val clint   = LazyModule(new CLINT(params = CLINTParams(), beatBytes = p(XLen) / 8))
  val intSink = IntSinkNode(Seq(IntSinkPortParameters(Seq(IntSinkParameters()))))

  val tlram = LazyModule(new TLRAM(address = new AddressSet(0x80000000L, 0x3fffff), beatBytes = p(CacheBlockBytes)))

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
  val l2cache       = LazyModule(new InclusiveCache(l2CacheParams, l2MicroParams))
  val cork          = LazyModule(new TLCacheCork)
  val lastLevelNode = cork.node

  // val tlBroadcast = LazyModule(new TLBroadcast(lineBytes = p(CacheBlockBytes), numTrackers = 1))

  val mbus   = LazyModule(new TLXbar)
  val tsi2tl = LazyModule(new TSIToTileLink)

  intSink := clint.intnode
  DisableMonitors { implicit p =>
    clint.node := TLBuffer(BufferParams(1, false, false)) := TLFragmenter(
      p(XLen) / 8,
      32,
      true,
      EarlyAck.AllPuts,
      true,
    )          := TLWidthWidget(p(CacheBlockBytes))       := mbus.node
    tlrom.node := TLBuffer(BufferParams(1, false, false)) := mbus.node
    tlram.node := lastLevelNode                           := l2cache.node := mbus.node
    mbus.node  := tsi2tl.node
  }

  lazy val module = new SimMemoryImp(this)
}

class SimMemoryImp(outer: SimMemory) extends LazyModuleImp(outer) {
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
