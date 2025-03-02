package ce

import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem.HierarchicalElementCrossingParamsLike
import freechips.rocketchip.tile.{
  AccumulatorExample,
  CustomCSR,
  HasCoreParameters,
  LazyRoCC,
  LazyRoCCModuleImp,
  LookupByHartIdImpl,
  OpcodeSet,
  RoCCInstruction,
  RocketTile,
  RocketTileModuleImp,
  RocketTileParams,
  TileVisibilityNodeKey,
  XLen,
}
import freechips.rocketchip.tilelink.{
  TLClientNode,
  TLManagerNode,
  TLMasterParameters,
  TLMasterPortParameters,
  TLSlaveParameters,
  TLSlavePortParameters,
}
import org.chipsalliance.cde.config.Parameters

/*
object RoCCIoBridge {
  def connect(ifc: RoCCIO, )
}
 */

class AccumulatorSuperModule(opcode: OpcodeSet, val n: Int = 4)(implicit p: Parameters)
  extends LazyRoCC(opcodes = opcode, roccCSRs = Seq(CustomCSR(id = 0x800, mask = 0xffffffffL, init = Some(0)))) {
  val accum       = LazyModule(new AccumulatorExample(opcode, n))
  lazy val module = new AccumulatorSuperModuleImp(this)
}

class AccumulatorSuperModuleImp(outer: AccumulatorSuperModule) extends LazyRoCCModuleImp(outer) with HasCoreParameters {
  // outer.accum.module.io <> io
  outer.accum.module.io := DontCare

  outer.accum.module.io.cmd <> io.cmd
  outer.accum.module.io.mem.req <> io.mem.req
  outer.accum.module.io.mem.resp <> io.mem.resp
  io.busy      := outer.accum.module.io.busy
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

  // override lazy val module = new RoCCIOBridgeImp(this)
}

class MyAccumulatorExampleModule extends Module {
  val n    = 4
  val xLen = 32
  val io   = IO(new SimpleRoCCCoreIO(32, 32, log2Ceil(n)))

  val regfile = Mem(n, UInt(xLen.W))
  val busy    = RegInit(VecInit(Seq.fill(n)(false.B)))

  val cmd        = Queue(io.cmd)
  val funct      = cmd.bits.inst.funct
  val addr       = cmd.bits.rs2(log2Up(n) - 1, 0)
  val doWrite    = funct === 0.U
  val doRead     = funct === 1.U
  val doLoad     = funct === 2.U
  val doAccum    = funct === 3.U
  val memRespTag = io.mem.resp.bits.tag(log2Up(n) - 1, 0)

  // datapath
  val addend = cmd.bits.rs1
  val accum  = regfile(addr)
  val wdata  = Mux(doWrite, addend, accum + addend)

  when(cmd.fire && (doWrite || doAccum)) {
    regfile(addr) := wdata
  }

  when(io.mem.resp.valid) {
    regfile(memRespTag) := io.mem.resp.bits.data
    busy(memRespTag)    := false.B
  }

  // control
  when(io.mem.req.fire) {
    busy(addr) := true.B
  }

  val doResp    = cmd.bits.inst.xd
  val stallReg  = busy(addr)
  val stallLoad = doLoad && !io.mem.req.ready
  val stallResp = doResp && !io.resp.ready

  when(cmd.fire) {
    printf(cf"New Command: ${cmd.bits.inst.funct}\n")
  }

  cmd.ready := !stallReg && !stallLoad && !stallResp
  // command resolved if no stalls AND not issuing a load that will need a request

  // PROC RESPONSE INTERFACE
  io.resp.valid := cmd.valid && doResp && !stallReg && !stallLoad
  // valid response if valid command, need a response, and no stalls
  io.resp.bits.rd := cmd.bits.inst.rd
  // Must respond with the appropriate tag or undefined behavior
  io.resp.bits.data := accum
  // Semantics is to always send out prior accumulator register value

  io.busy := cmd.valid || busy.reduce(_ || _)
  // Be busy when have pending memory requests or committed possibility of pending requests
  io.interrupt := false.B
  // Set this true to trigger an interrupt on the processor (please refer to supervisor documentation)

  io.mem.req.bits := DontCare
  // MEMORY REQUEST INTERFACE
  io.mem.req.valid       := cmd.valid && doLoad && !stallReg && !stallResp
  io.mem.req.bits.addr   := addend
  io.mem.req.bits.tag    := addr
  io.mem.req.bits.cmd    := M_XRD // perform a load (M_XWR for stores)
  io.mem.req.bits.size   := log2Ceil(8).U
  io.mem.req.bits.signed := false.B
  io.mem.req.bits.data   := 0.U   // we're not performing any stores...
  io.mem.req.bits.phys   := false.B
  io.mem.req.bits.dprv   := cmd.bits.status.dprv
  io.mem.req.bits.dv     := cmd.bits.status.dv
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
  val xLen      = outer.p(XLen)
  val addrWidth = outer.p(XLen)
  val tagWidth  = 5

  val roccifc = IO(Flipped(new SimpleRoCCCoreIO(xLen, addrWidth, tagWidth)))
  require(io.mem.req.bits.addr.getWidth >= roccifc.mem.req.bits.addr.getWidth)
  require(io.mem.req.bits.tag.getWidth >= roccifc.mem.req.bits.tag.getWidth)
  require(io.csrs.length == roccifc.csrs.length)

  io.mem.req <> roccifc.mem.req
  roccifc.mem.resp.valid     := io.mem.resp.valid
  roccifc.mem.resp.bits.data := io.mem.resp.bits.data
  roccifc.mem.resp.bits.mask := io.mem.resp.bits.mask
  roccifc.mem.resp.bits.tag  := io.mem.resp.bits.tag

  io.cmd <> roccifc.cmd
  io.resp <> roccifc.resp
  io.busy           := roccifc.busy
  io.interrupt      := roccifc.interrupt
  roccifc.exception := io.exception
  roccifc.csrs <> io.csrs
}

class SimpleRoCCCommand(xLen: Int) extends Bundle {
  val inst   = new RoCCInstruction
  val rs1    = Bits(xLen.W)
  val rs2    = Bits(xLen.W)
  val status = new MStatus
}

class SimpleRoCCResponse(xLen: Int) extends Bundle {
  val rd   = Bits(5.W)
  val data = Bits(xLen.W)
}

class SimpleHellaCacheReq(xLen: Int, addrWidth: Int, tagWidth: Int) extends Bundle {
  val dataBytes = xLen / 8

  val addr   = UInt(addrWidth.W)
  val tag    = UInt(tagWidth.W)
  val cmd    = UInt(M_SZ.W)
  val size   = UInt(log2Ceil(log2Ceil(dataBytes) + 1).W)
  val signed = Bool()
  val dprv   = UInt(PRV.SZ.W)
  val dv     = Bool()

  val data = UInt(xLen.W)
  val mask = UInt(dataBytes.W)

  val phys     = Bool()
  val no_alloc = Bool()
  val no_xcpt  = Bool()
}

class SimpleHellaCacheResp(xLen: Int, tagWidth: Int) extends Bundle {
  val dataBytes = xLen / 8

  val tag  = UInt(tagWidth.W)
  val data = UInt(xLen.W)
  val mask = UInt(dataBytes.W)
}

class SimpleHellaCacheIO(xLen: Int, addrWidth: Int, tagWidth: Int) extends Bundle {
  val req  = Decoupled(new SimpleHellaCacheReq(xLen, addrWidth, tagWidth))
  val resp = Flipped(Valid(new SimpleHellaCacheResp(xLen, tagWidth)))
}

class SimpleCustomCSRIO(xLen: Int) extends Bundle {
  val ren   = Output(Bool())       // set by CSRFile, indicates an instruction is reading the CSR
  val wen   = Output(Bool())       // set by CSRFile, indicates an instruction is writing the CSR
  val wdata = Output(UInt(xLen.W)) // wdata provided by instruction writing CSR
  val value = Output(UInt(xLen.W)) // current value of CSR in CSRFile

  val stall = Input(Bool()) // reads and writes to this CSR should stall (must be bounded)

  val set   = Input(Bool()) // set/sdata enables external agents to set the value of this CSR
  val sdata = Input(UInt(xLen.W))
}

class SimpleRoCCCoreIO(xLen: Int, addrWidth: Int, tagWidth: Int, nRoCCCSRs: Int = 0) extends Bundle {
  val cmd       = Flipped(Decoupled(new SimpleRoCCCommand(xLen)))
  val resp      = Decoupled(new SimpleRoCCResponse(xLen))
  val mem       = new SimpleHellaCacheIO(xLen, addrWidth, tagWidth)
  val busy      = Output(Bool())
  val interrupt = Output(Bool())
  val exception = Input(Bool())
  val csrs      = Flipped(Vec(nRoCCCSRs, new SimpleCustomCSRIO(xLen)))
}

class RocketTileWithRoCCIO(
  params:   RocketTileParams,
  crossing: HierarchicalElementCrossingParamsLike,
  lookup:   LookupByHartIdImpl,
)(
  implicit p: Parameters,
) extends RocketTile(params, crossing, lookup) {
  override lazy val module = new RocketTileWithRoCCIOImp(this)
}

class RocketTileWithRoCCIOImp(outer: RocketTileWithRoCCIO) extends RocketTileModuleImp(outer) {
  val addrWidth = outer.p(XLen)
  val tagWidth  = 5

  val roccifc = if (p(InsertRoCCIO)) Some(IO(Flipped(new SimpleRoCCCoreIO(xLen, addrWidth, tagWidth)))) else None

  roccifc.map { i =>
    val ioBridge = outer.roccs(0).module.asInstanceOf[RoCCIOBridgeImp]
    ioBridge.roccifc.busy := i.busy
    ioBridge.roccifc.cmd <> i.cmd
    ioBridge.roccifc.resp <> i.resp
    ioBridge.roccifc.mem <> i.mem
    ioBridge.roccifc.interrupt := i.interrupt
    i.exception                := ioBridge.roccifc.exception
  }
}
