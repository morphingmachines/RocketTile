package ce

import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile.{AccumulatorExample, OpcodeSet, TileVisibilityNodeKey}
import freechips.rocketchip.tilelink.{
  TLClientNode,
  TLManagerNode,
  TLMasterParameters,
  TLMasterPortParameters,
  TLSlaveParameters,
  TLSlavePortParameters,
}
import org.chipsalliance.cde.config.Parameters

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
  val io   = IO(new simpleRoCC.SimpleRoCCCoreIO(32, 32, log2Ceil(n)))

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
