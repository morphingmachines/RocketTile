package ce.sim

import chisel3._
import freechips.rocketchip.diplomacy.{AddressSet, LazyModule, LazyModuleImp, SimpleDevice}
import freechips.rocketchip.regmapper.RegField
import freechips.rocketchip.tilelink.TLRegisterNode
import org.chipsalliance.cde.config.Parameters

class SimToHost(implicit p: Parameters) extends LazyModule {
  val device = new SimpleDevice("toHost-dont-care", Seq())
  val regNode = TLRegisterNode(
    address = Seq(AddressSet(0x4000000, 0xff)),
    device = device,
    beatBytes = 4,
    concurrency = 1,
  )

  lazy val module = new SimToHostImp(this)
}

class SimToHostImp(outer: SimToHost) extends LazyModuleImp(outer) {
  val io        = IO(UInt(32.W))
  val toHostReg = RegInit(0.U(32.W))
  outer.regNode.regmap(
    0x00 -> Seq(RegField(32, toHostReg)),
  )
  io := toHostReg
}
