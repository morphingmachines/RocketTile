package ce

import chisel3._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.rocket.{DCacheParams, ICacheParams, MulDivParams, RocketCoreParams}
import freechips.rocketchip.subsystem.{CacheBlockBytes, RocketCrossingParams}
import freechips.rocketchip.tile.{HartsWontDeduplicate, MaxHartIdBits, NMI, RocketTile, RocketTileParams, XLen}
import freechips.rocketchip.tilelink.{TLManagerNode, TLSlaveParameters, TLSlavePortParameters}
import org.chipsalliance.cde.config.{Config, Field, Parameters}

case object CETileParams extends Field[RocketTileParams]

class CEConfig
  extends Config((_, _, site) => {
    case XLen          => 32
    case MaxHartIdBits => 8
    // case BuildRoCC =>
    //  List { (p: Parameters) =>
    //    val blackbox = LazyModule(new BlackBoxExample(OpcodeSet.custom3, "RoccBlackBox")(p))
    //    blackbox
    //  }
    case CacheBlockBytes => 4
    case CETileParams => {
      RocketTileParams(
        core = RocketCoreParams(
          useVM = false,
          fpu = None,
          mulDiv = Some(MulDivParams(mulUnroll = 8)),
          useNMI = true,
          clockGate = true,
          mtvecWritable = true,
          mtvecInit = Some(BigInt(0x10000)),
        ),
        btb = None,
        dcache = Some(
          DCacheParams(
            rowBits = 32, // site(SystemBusKey).beatBits,
            nSets = 256,  // 16Kb scratchpad
            nWays = 1,
            nTLBSets = 1,
            nTLBWays = 4,
            nMSHRs = 0,
            blockBytes = site(CacheBlockBytes),
            // scratch = Some(0x80000000L),
          ),
        ),
        icache = Some(
          ICacheParams(
            rowBits = 32, // site(SystemBusKey).beatBits
            nSets = 64,
            nWays = 1,
            nTLBSets = 1,
            nTLBWays = 4,
            blockBytes = site(CacheBlockBytes),
          ),
        ),
      )
    }
  })

class CERISCV()(implicit p: Parameters = new Config(new CEConfig)) extends LazyModule with BindingScope {
  val cetile = LazyModule(
    new RocketTile(p(CETileParams), RocketCrossingParams(), HartsWontDeduplicate(p(CETileParams))),
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
  cetile.nmiNode := BundleBridgeSource[NMI](None)

  // core halts on non-recoverable error. ECC error in DCache cause such an error. Enable ECC in DCsChe to access this signal.
  IntSinkNode(IntSinkPortSimple()) := cetile.haltNode

  // core ceases on clockGate. Enable clockGate to access this signal
  IntSinkNode(IntSinkPortSimple()) := cetile.ceaseNode

  override lazy val module = new CERISCVImp(this)
}

class CERISCVImp(outer: CERISCV) extends LazyModuleImp(outer) {

  val interrupts = IO(new Bundle {
    val debug = Input(Bool())
    val msip  = Input(Bool())
    val mtip  = Input(Bool())
    val meip  = Input(Bool())
  })

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

  val tile_master_sink = TLManagerNode(
    Seq(TLSlavePortParameters.v1(Seq(ramParams, bootROMParams), beatBytes = 4, minLatency = 1, endSinkId = 1)),
  )(ValName("memport"))
  tile_master_sink := cetile.masterNode
  val memportIO = InModuleBody(tile_master_sink.makeIOs())

}

class CeTop extends CERISCV with WithMemPortIO {
  override lazy val module = new CeTopImp(this)
}

class CeTopImp(outer: CERISCV) extends CERISCVImp(outer) {}
