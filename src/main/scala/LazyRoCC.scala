package ce

import chisel3._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.HierarchicalElementCrossingParamsLike
import freechips.rocketchip.tile.{
  CustomCSR,
  HasLazyRoCC,
  LazyRoCC,
  LazyRoCCModuleImp,
  LookupByHartIdImpl,
  OpcodeSet,
  RocketTile,
  RocketTileParams,
  TileVisibilityNodeKey,
}
import freechips.rocketchip.tilelink.{
  TLClientNode,
  TLManagerNode,
  TLMasterParameters,
  TLMasterPortParameters,
  TLSlaveParameters,
  TLSlavePortParameters,
}
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.tile.AccumulatorExample
import freechips.rocketchip.tile.HasCoreParameters
//import freechips.rocketchip.tile.RoCCInstruction

case object BuildRoCCIOBridge extends Field[Boolean]

/*
object RoCCIoBridge {
  def connect(ifc: RoCCIO, )
}
 */


class AccumulatorSuperModule(opcode: OpcodeSet, val n: Int = 4)(implicit p: Parameters) extends LazyRoCC(opcode) {
  val accum = LazyModule(new AccumulatorExample(opcode, n))
  lazy val module = new AccumulatorSuperModuleImp(this)
}

class AccumulatorSuperModuleImp(outer: AccumulatorSuperModule) extends LazyRoCCModuleImp(outer) with HasCoreParameters {
  //outer.accum.module.io <> io
  outer.accum.module.io := DontCare

  outer.accum.module.io.cmd <> io.cmd
  outer.accum.module.io.mem.req <> io.mem.req
  outer.accum.module.io.mem.resp <> io.mem.resp
  io.busy := outer.accum.module.io.busy
  io.interrupt := outer.accum.module.io.interrupt
  io.resp <> outer.accum.module.io.resp
}

class AccumulatorWrapper(opcodes: OpcodeSet = OpcodeSet.custom0)(implicit p: Parameters)
  extends AccumulatorExample(opcodes) {

  val dummySlaveParams = TLSlaveParameters.v1(
    address = Seq(AddressSet(BigInt(0x80000000L), 0xfff)),
    regionType = RegionType.UNCACHED,
    supportsPutFull = TransferSizes(1, 64),
    supportsGet = TransferSizes(1, 64),
  )

  val dummySlave  = TLManagerNode(Seq(TLSlavePortParameters.v1(Seq(dummySlaveParams), beatBytes = 4)))
  val dummyMaster = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(name = "dummyMaster")))))

  DisableMonitors { implicit p =>
    /* Dummy Slave and Sink, that emulates intra-tile master and the rest of the system it communicates with.
       The tile see the rest of the system through "p(TileVisibiltyNodeKey)". Since RoCC Accelerator is part of the Tile,
       any tile-link master interface of the RoCC accelerator must connect to other slaves in the rest of the system through
       p(TileVisibilityNodeKey). In a standalone RoCC accelerator module, without any TL master interface, we will still need
       to know p(TileVisibilityNodeKey) for determining the physical address bit-width, to interface with L1DCache.
     */
    dummySlave := p(TileVisibilityNodeKey) := dummyMaster
  }

  //override lazy val module = new RoCCIOBridgeImp(this)
}
class RoCCIOBridge(opcodes: OpcodeSet = OpcodeSet.custom0, roccCSRs: Seq[CustomCSR] = Nil)(implicit p: Parameters)
  extends LazyRoCC(opcodes, roccCSRs = roccCSRs) {

  val dummySlaveParams = TLSlaveParameters.v1(
    address = Seq(AddressSet(BigInt(0x80000000L), 0xfff)),
    regionType = RegionType.UNCACHED,
    supportsPutFull = TransferSizes(1, 64),
    supportsGet = TransferSizes(1, 64),
  )

  val dummySlave  = TLManagerNode(Seq(TLSlavePortParameters.v1(Seq(dummySlaveParams), beatBytes = 4)))
  val dummyMaster = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(name = "dummyMaster")))))

  DisableMonitors { implicit p =>
    /* Dummy Slave and Sink, that emulates intra-tile master and the rest of the system it communicates with.
       The tile see the rest of the system through "p(TileVisibiltyNodeKey)". Since RoCC Accelerator is part of the Tile,
       any tile-link master interface of the RoCC accelerator must connect to other slaves in the rest of the system through
       p(TileVisibilityNodeKey). In a standalone RoCC accelerator module, without any TL master interface, we will still need
       to know p(TileVisibilityNodeKey) for determining the physical address bit-width, to interface with L1DCache.
     */
    dummySlave := p(TileVisibilityNodeKey) := dummyMaster
  }

  override lazy val module = new RoCCIOBridgeImp(this)
}

class RoCCIOBridgeImp(outer: RoCCIOBridge) extends LazyRoCCModuleImp(outer) {

  val roccifc = IO(Flipped(chiselTypeOf(io)))
  roccifc <> io
}

trait HasRoCCIOBridge { this: HasLazyRoCC =>
  roccs.foreach { i =>
    i match {
      case x: RoCCIOBridge => {
        InModuleBody {
          val roccIOBridge = IO(chiselTypeOf(x.module.roccifc))
          roccIOBridge := x.module.roccifc
        }
      }
    }
  }
}

class RocketTileWithRoCCIO(
  params:   RocketTileParams,
  crossing: HierarchicalElementCrossingParamsLike,
  lookup:   LookupByHartIdImpl,
)(
  implicit p: Parameters,
) extends RocketTile(params, crossing, lookup)
  with HasRoCCIOBridge {}
