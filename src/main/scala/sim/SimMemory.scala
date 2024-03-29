package ce.sim

import chisel3._
import freechips.rocketchip.devices.tilelink.{BootROMParams, CLINT, CLINTParams, TLROM}
import freechips.rocketchip.diplomacy.{AddressSet, BufferParams, DisableMonitors, LazyModule, LazyModuleImp}
import freechips.rocketchip.interrupts.{IntSinkNode, IntSinkParameters, IntSinkPortParameters}
import freechips.rocketchip.subsystem.CacheBlockBytes
import freechips.rocketchip.tilelink.{TLBroadcast, TLBuffer, TLFIFOFixer, TLRAM, TLXbar}
import org.chipsalliance.cde.config.Parameters
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

  val clint   = LazyModule(new CLINT(params = CLINTParams(), beatBytes = p(CacheBlockBytes)))
  val intSink = IntSinkNode(Seq(IntSinkPortParameters(Seq(IntSinkParameters()))))

  val tlram       = LazyModule(new TLRAM(address = new AddressSet(0x80000000L, 0x3fffff), beatBytes = p(CacheBlockBytes)))
  val tlBroadcast = LazyModule(new TLBroadcast(lineBytes = p(CacheBlockBytes), numTrackers = 1))

  val mbus   = LazyModule(new TLXbar)
  val rambus = LazyModule(new TLXbar)
  val tsi2tl = LazyModule(new TSIToTileLink)

  val iobus = LazyModule(new TLXbar)
  intSink := clint.intnode
  DisableMonitors { implicit p =>
    clint.node := iobus.node  := TLFIFOFixer(TLFIFOFixer.all)            := TLBuffer(BufferParams(1, false, false)) := mbus.node
    tlrom.node := rambus.node
    tlram.node := rambus.node := TLBuffer(BufferParams(2, false, false)) := tlBroadcast.node                        := mbus.node
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
