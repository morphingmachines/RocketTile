package ce.sim

import ce.{CEConfig, CERISCV}
import chisel3._
import freechips.rocketchip.diplomacy.{DisableMonitors, LazyModule, LazyModuleImp}
import org.chipsalliance.cde.config.{Config, Parameters}
import testchipip.tsi.SimTSI
class SimDUT(implicit p: Parameters) extends LazyModule {
  val ce     = LazyModule(new CERISCV()(new Config(new CEConfig())))
  val mem    = LazyModule(new SimMemory)

  DisableMonitors { implicit p: Parameters =>
    mem.mbus.node  := ce.cetile.masterNode
  }
  lazy val module = new SimDUTImp(this)
}

class SimDUTImp(outer: SimDUT) extends LazyModuleImp(outer) with TestHarnessShell {
  outer.ce.module.interrupts           := DontCare
  outer.ce.hartIdIO                    := DontCare
  outer.ce.bootROMResetVectorAddressIO := 0x10040.U
  io.success                           := SimTSI.connect(Some(outer.mem.module.io.tsi), clock, reset)
  outer.ce.module.interrupts.msip      := outer.mem.module.io.msip
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
  val ldut = LazyModule(new SimDUT()(Parameters.empty))
  val dut  = Module(ldut.module)
  io.success := dut.io.success
}
