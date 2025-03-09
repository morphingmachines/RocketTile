package ce.sim

import ce._
import ce.simpleRoCC.DMA
import chisel3._
import freechips.rocketchip.diplomacy.{AddressSet, LazyModule, LazyModuleImp}
import freechips.rocketchip.subsystem.CacheBlockBytes
import freechips.rocketchip.tilelink.TLRAM
import org.chipsalliance.cde.config.Parameters
import testchipip.tsi.SimTSI

abstract class BaseDUT(implicit p: Parameters) extends LazyModule {

  val ramOffsetAddrWidth = 22
  val ce                 = LazyModule(new CERISCV)
  val tlram = LazyModule(
    new TLRAM(address = new AddressSet(0x80000000L, (1 << ramOffsetAddrWidth) - 1), beatBytes = p(CacheBlockBytes)),
  )

  val uncore = if (p(InsertL2Cache)) {
    LazyModule(new Uncore with HasL2Cache)
  } else {
    LazyModule(new Uncore with HasNoL2Cache)
  }

  uncore.mbus.node := ce.cetile.masterNode
  tlram.node       := uncore.memoryNode

}

class SimDUT(implicit p: Parameters) extends BaseDUT {
  lazy val module = new SimDUTImp(this)
}

class SimDUTImp(outer: SimDUT) extends LazyModuleImp(outer) with emitrtl.TestHarnessShell {
  outer.ce.module.interrupts           := DontCare
  outer.ce.hartIdIO                    := DontCare
  outer.ce.bootROMResetVectorAddressIO := 0x10040.U
  io.success                           := SimTSI.connect(Some(outer.uncore.module.io.tsi), clock, reset)
  outer.ce.module.interrupts.msip      := outer.uncore.module.io.msip

  if (outer.p(simpleRoCC.InsertRoCCIO)) {
    val accum = Module(new simpleRoCC.Accumulator)
    outer.ce.module.roccIO.get <> accum.io
  }
}

class SimDUTWithRoCCIODMA(implicit p: Parameters) extends BaseDUT {
  require(p(simpleRoCC.InsertRoCCIO))
  val dma = LazyModule(new DMA(2, 32))
  uncore.mbus.node := dma.rdClient
  uncore.mbus.node := dma.wrClient

  lazy val module = new SimDUTWithRoCCIODMAImp(this)
}

class SimDUTWithRoCCIODMAImp(outer: SimDUTWithRoCCIODMA) extends LazyModuleImp(outer) with emitrtl.TestHarnessShell {
  outer.ce.module.interrupts           := DontCare
  outer.ce.hartIdIO                    := DontCare
  outer.ce.bootROMResetVectorAddressIO := 0x10040.U
  io.success                           := SimTSI.connect(Some(outer.uncore.module.io.tsi), clock, reset)
  outer.ce.module.interrupts.msip      := outer.uncore.module.io.msip

  outer.ce.module.roccIO.get <> outer.dma.module.io
}
