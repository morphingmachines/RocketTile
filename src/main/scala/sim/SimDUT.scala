package ce.sim

import ce.{CERISCV, RV32Config, _}
import chisel3._
import freechips.rocketchip.diplomacy.{AddressSet, LazyModule, LazyModuleImp}
import freechips.rocketchip.subsystem.CacheBlockBytes
import freechips.rocketchip.tilelink.TLRAM
import org.chipsalliance.cde.config.{Config, Parameters}
import testchipip.tsi.SimTSI
class SimDUT(implicit p: Parameters) extends LazyModule {

  val ce    = LazyModule(new CERISCV)
  val tlram = LazyModule(new TLRAM(address = new AddressSet(0x80000000L, 0x3fffff), beatBytes = p(CacheBlockBytes)))

  val uncore = if (p(InsertL2Cache)) {
    LazyModule(new Uncore with HasL2Cache)
  } else {
    LazyModule(new Uncore with HasNoL2Cache)
  }

  uncore.mbus.node := ce.cetile.masterNode
  tlram.node       := uncore.memoryNode

  lazy val module = new SimDUTImp(this)
}

class SimDUTImp(outer: SimDUT) extends LazyModuleImp(outer) with TestHarnessShell {
  outer.ce.module.interrupts           := DontCare
  outer.ce.hartIdIO                    := DontCare
  outer.ce.bootROMResetVectorAddressIO := 0x10040.U
  io.success                           := SimTSI.connect(Some(outer.uncore.module.io.tsi), clock, reset)
  outer.ce.module.interrupts.msip      := outer.uncore.module.io.msip
}

trait TestHarnessShell extends Module {
  val io = IO(new Bundle { val success = Output(Bool()) })
}

//dut is passed as call-by-name parameter as Module instantiate should be wrapped in Module()
class TestHarness(dut: => TestHarnessShell) extends Module {
  val io = IO(new Bundle { val success = Output(Bool()) })
  io.success := Module(dut).io.success
}

class DUT extends Module with TestHarnessShell {
  val ldut = LazyModule(new SimDUT()(new Config(new RV32Config)))
  val dut  = Module(ldut.module)
  io.success := dut.io.success
}
