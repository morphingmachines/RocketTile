package ce

import chisel3._
import chiseltest._
import chiseltest.simulator.{SimulatorAnnotation, WriteWaveformAnnotation}
import freechips.rocketchip.diplomacy.LazyModule
import org.chipsalliance.cde.config.Parameters
import org.scalatest.freespec.AnyFreeSpec

import scala.util.control.Breaks._

class CeTileTests extends AnyFreeSpec with ChiselScalatestTester {
  protected def DefaultBackend:         SimulatorAnnotation     = VerilatorBackendAnnotation
  protected def DefaultWriteWaveformat: WriteWaveformAnnotation = WriteVcdAnnotation
  private def DefaultAnnos = Seq(DefaultBackend, DefaultWriteWaveformat)
  private def name         = DefaultBackend.getSimulator.name

  s"my dumy test with $name" in {
    test(LazyModule(new sim.SimDUT()(Parameters.empty)).module).withAnnotations(DefaultAnnos) { c =>
      c.reset.poke(true.B)
      c.clock.step(8)
      c.reset.poke(false.B)
      // LoadRAM.LoadFile("generators/cetile/src/test/resources/rv32ui-r-addi/rv32ui-r-addi.img", c)
      breakable {
        for (_ <- 0 until 1000) {
          c.clock.step(1)
        }
      }
    }
  }
}
