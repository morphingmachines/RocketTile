package ce

import emitrtl._
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.tile.OpcodeSet

/** To run from a terminal shell
  * {{{
  * mill ce.runMain ce.ceMain CE
  * }}}
  */

object ceMain extends App with LazyToplevel {
  import org.chipsalliance.cde.config.{Parameters, Config}
  val str = if (args.length == 0) "" else args(0)
  val lazyTop = str match {
    case "CE"         => LazyModule(new ce.CeTop()(new Config(new RV32Config)))
    case "RV32"       => LazyModule(new ce.sim.SimDUT()(new Config(new RV32Config)))
    case "RV64RoCC"   => LazyModule(new ce.sim.SimDUT()(new Config(new RV64WithRoCCAccConfig)))
    case "RV32RoCCIO" => LazyModule(new ce.sim.SimDUT()(new Config(new RV32WithRoCCIOConfig)))
    case "RoCCIO" => {
      import freechips.rocketchip.tile.TileVisibilityNodeKey
      import freechips.rocketchip.tilelink.TLEphemeralNode
      import freechips.rocketchip.diplomacy.ValName
      val p: Parameters =
        (new Config(new RV32Config)).alterMap(Map(TileVisibilityNodeKey -> TLEphemeralNode()(ValName("tile_master"))))
      LazyModule(new simpleRoCC.RoCCIOBridgeTop()(p))
    }
    case "AccumAccel" => {
      import freechips.rocketchip.tile.TileVisibilityNodeKey
      import freechips.rocketchip.tilelink.TLEphemeralNode
      import freechips.rocketchip.diplomacy.ValName
      val p: Parameters =
        (new Config(new RV32Config)).alterMap(Map(TileVisibilityNodeKey -> TLEphemeralNode()(ValName("tile_master"))))
      LazyModule(new AccumulatorWrapper(OpcodeSet.custom0)(p))
    }
    // case _ => LazyModule(new ce.CeTop()(new Config(new RV32Config)))
    case _ => throw new Exception("Unknown Module Name!")
  }

  showModuleComposition(lazyTop)
  chisel2firrtl()
  firrtl2sv()
  genDiplomacyGraph()

}

object TestLazyMain extends App with LazyToplevel with VerilateTestHarness with WithLazyModuleDUT {
  import org.chipsalliance.cde.config.Config
  val str = if (args.length == 0) "" else args(0)
  lazy val lazyTop = str match {
    case "RV32"        => LazyModule(new ce.sim.SimDUT()(new Config(new RV32Config)))
    case "RV64"        => LazyModule(new ce.sim.SimDUT()(new Config(new RV64WithL2)))
    case "RV32RoCC"    => LazyModule(new ce.sim.SimDUT()(new Config(new RV32WithRoCCIOConfig)))
    case "RV32RoCCDMA" => LazyModule(new ce.sim.SimDUTWithRoCCIODMA()(new Config(new RV32WithRoCCIOConfig)))
    case "RV64RoCC"    => LazyModule(new ce.sim.SimDUT()(new Config(new RV64WithRoCCAccConfig)))
    case _             => throw new Exception("Unknown Module Name!")
  }

  chisel2firrtl()
  genDiplomacyGraph()
  firrtl2sv()
  verilate()
  build()
}
