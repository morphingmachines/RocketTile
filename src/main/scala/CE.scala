package ce

import chisel3._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.subsystem.RocketCrossingParams
import freechips.rocketchip.tile.{HartsWontDeduplicate, NMI, RocketTileParams, TileKey, TraceBundle, XLen}
import freechips.rocketchip.tilelink.{TLManagerNode, TLSlaveParameters, TLSlavePortParameters}
import org.chipsalliance.cde.config.Parameters

class CERISCV(implicit p: Parameters) extends LazyModule with BindingScope {
  val cetile = LazyModule(
    new simpleRoCC.RocketTileWithRoCCIO(
      p(TileKey).asInstanceOf[RocketTileParams],
      RocketCrossingParams(),
      HartsWontDeduplicate(p(TileKey)),
    ),
  )

  val intSourcePortParams = IntSourcePortParameters(sources =
    Seq(IntSourceParameters(range = IntRange(0, 4))),
  ) // Edge with four interrupt signals; Vec[Bool] of length 4
  val intsrcnode = IntSourceNode(portParams = Seq(intSourcePortParams)) // portParams length=1 for one edge.
  /*
   * @todo: Find out how these vec[Bool] extracted and mapped appropriately to individual interrupt sources.
   */
  cetile.intInwardNode := intsrcnode

  val extsrcharId = BundleBridgeSource[UInt](Some(() => UInt(8.W)))
  cetile.hartIdNode := extsrcharId
  val hartIdIO = InModuleBody(extsrcharId.makeIO())

  val bootROMResetVectorSourceNode = BundleBridgeSource[UInt](Some(() => UInt(32.W)))
  cetile.resetVectorNode := bootROMResetVectorSourceNode
  val bootROMResetVectorAddressIO = InModuleBody(bootROMResetVectorSourceNode.makeIO())

  // Reports when the core is waiting for an interrupt
  val wfiNodeSink = IntSinkNode(IntSinkPortSimple())
  wfiNodeSink := cetile.wfiNode
  val wfiIO = InModuleBody(wfiNodeSink.makeIOs())

  // Enable non-maskable interrupts to access this.
  cetile.nmiNode.map(_ := BundleBridgeSource[NMI](None))

  // core halts on non-recoverable error. ECC error in DCache cause such an error. Enable ECC in DCache to access this signal.
  IntSinkNode(IntSinkPortSimple()) := cetile.haltNode

  // core ceases on clockGate. Enable clockGate to access this signal
  IntSinkNode(IntSinkPortSimple()) := cetile.ceaseNode

  val traceSink = BundleBridgeSink[TraceBundle](None)
  traceSink := cetile.traceSourceNode

  override lazy val module = new CERISCVImp(this)
}

class CERISCVImp(outer: CERISCV) extends LazyModuleImp(outer) {

  val interrupts = IO(new Bundle {
    val debug = Input(Bool())
    val msip  = Input(Bool())
    val mtip  = Input(Bool())
    val meip  = Input(Bool())
  })

  override implicit val p: Parameters = outer.cetile.p
  val xLen      = outer.p(XLen)
  val addrWidth = outer.p(XLen)
  val tagWidth  = 5
  val roccIO =
    if (p(simpleRoCC.InsertRoCCIO)) Some(IO(Flipped(new simpleRoCC.SimpleRoCCCoreIO(xLen, addrWidth, tagWidth))))
    else None

  if (p(simpleRoCC.InsertRoCCIO)) {
    outer.cetile.module.roccifc.map(i => i <> roccIO.get)
  }

  outer.intsrcnode.out(0)._1(0) := interrupts.debug
  outer.intsrcnode.out(0)._1(1) := interrupts.msip
  outer.intsrcnode.out(0)._1(2) := interrupts.mtip
  outer.intsrcnode.out(0)._1(3) := interrupts.meip

}

trait WithMemPortIO { this: CERISCV =>
  val ramParams = TLSlaveParameters.v1(
    address = Seq(AddressSet(BigInt(0x80000000L), 0xfff)),
    regionType = RegionType.UNCACHED,
    executable = true,
    supportsAcquireT = TransferSizes(1, 64),
    supportsAcquireB = TransferSizes(1, 64),
    supportsPutFull = TransferSizes(1, 64),
    supportsPutPartial = TransferSizes(1, 64),
    supportsGet = TransferSizes(1, 64),
    alwaysGrantsT = true,
  )

  val bootROMParams = TLSlaveParameters.v1(
    address = Seq(AddressSet(0x10000, 0xff)),
    regionType = RegionType.UNCACHED,
    executable = true,
    supportsGet = TransferSizes(1, 64),
  )

  val tile_sink = TLManagerNode(
    Seq(TLSlavePortParameters.v1(Seq(ramParams, bootROMParams), beatBytes = 4, minLatency = 1, endSinkId = 1)),
  )(ValName("memport"))
  tile_sink := cetile.masterNode
  val memportIO = InModuleBody(tile_sink.makeIOs())

}

class CeTop(implicit p: Parameters) extends CERISCV with WithMemPortIO {
  override lazy val module = new CeTopImp(this)
}

class CeTopImp(outer: CERISCV) extends CERISCVImp(outer) {}
