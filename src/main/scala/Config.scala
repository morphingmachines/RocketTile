package ce

import emitrtl.ConfigPrinter
import freechips.rocketchip.rocket.{DCacheParams, ICacheParams, MulDivParams, RocketCoreParams}
import freechips.rocketchip.subsystem.{CacheBlockBytes, WithInclusiveCache, WithoutTLMonitors}
import freechips.rocketchip.tile.{AccumulatorExample, BuildRoCC, MaxHartIdBits, OpcodeSet, RocketTileParams, TileKey}
import org.chipsalliance.cde.config.{Config, Field, Parameters}
import org.chipsalliance.diplomacy.lazymodule.LazyModule
class CEConfig
  extends Config((_, here, _) => {
    case CacheBlockBytes => 32
    case TileKey => {
      RocketTileParams(
        core = RocketCoreParams(
          xLen = 32,
          pgLevels = 2,
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
            rowBits = here(CacheBlockBytes) * 8,
            nSets = 256,
            nWays = 1,
            nTLBSets = 1,
            nTLBWays = 4,
            nMSHRs = 0,
            blockBytes = here(CacheBlockBytes),
          ),
        ),
        icache = Some(
          ICacheParams(
            rowBits = here(CacheBlockBytes) * 8,
            nSets = 64,
            nWays = 1,
            nTLBSets = 1,
            nTLBWays = 4,
            blockBytes = here(CacheBlockBytes),
          ),
        ),
      )
    }
    case MaxHartIdBits => 8
  })

class CEConfig64
  extends Config((_, here, _) => {
    case CacheBlockBytes => 64
    case TileKey => {
      RocketTileParams(
        core = RocketCoreParams(
          xLen = 64,
          pgLevels = 3,
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
            rowBits = here(CacheBlockBytes) * 8,
            nSets = 256,
            nWays = 1,
            nTLBSets = 1,
            nTLBWays = 4,
            nMSHRs = 0,
            blockBytes = here(CacheBlockBytes),
          ),
        ),
        icache = Some(
          ICacheParams(
            rowBits = here(CacheBlockBytes) * 8,
            nSets = 64,
            nWays = 1,
            nTLBSets = 1,
            nTLBWays = 4,
            blockBytes = here(CacheBlockBytes),
          ),
        ),
      )
    }
    case MaxHartIdBits => 8
  })

class WithAccumulatorRoCCExample
  extends Config((_, _, up) => {
    case BuildRoCC => {
      val otherRoccAcc = up(BuildRoCC)
      List { (p: Parameters) =>
        val roccAcc = LazyModule(new AccumulatorExample(OpcodeSet.custom0)(p))
        roccAcc
      } ++ otherRoccAcc
    }
  })

class WithRoCCBridge
  extends Config((_, _, _) => {
    case simpleRoCC.InsertRoCCIO => true
    case BuildRoCC => {
      // val otherRoccAcc = up(BuildRoCC)
      List { (p: Parameters) =>
        val roccBridge = LazyModule(new simpleRoCC.RoCCIOBridge(OpcodeSet.custom0)(p))
        roccBridge
      } // ++ otherRoccAcc
    }
  })

case object BootROMFile extends Field[String]
class WithBootROMFile
  extends Config((site, _, _) => {
    case BootROMFile => {
      if (site(TileKey).core.xLen == 64)
        "./src/main/resources/bootrom/bootrom.rv64.img"
      else
        "./src/main/resources/bootrom/bootrom.rv32.img"
    }
  })

case object InsertL2Cache extends Field[Boolean](false)

class WithL2Cache
  extends Config((_, _, _) => { case InsertL2Cache =>
    true
  })

class RV32Config            extends Config(new WithBootROMFile ++ (new CEConfig))
class RV32WithRoCCAccConfig extends Config(new WithAccumulatorRoCCExample ++ new RV32Config)
class RV32WithRoCCIOConfig  extends Config(new WithRoCCBridge ++ new RV32Config)
class RV64Config            extends Config(new WithBootROMFile ++ (new CEConfig64))
class RV64WithRoCCAccConfig extends Config(new WithAccumulatorRoCCExample ++ new RV64Config)

class RV32WithL2 extends Config(new RV32Config ++ new WithL2Cache)
class RV64WithL2 extends Config(new RV64Config ++ new WithL2Cache)

class RV32WithNoTLMonitors extends Config(new RV32Config ++ new WithoutTLMonitors ++ new WithInclusiveCache)

object RocketTileConfigPrinter {
  def printConfig(implicit p: Parameters) = {

    val xLen = p(TileKey).core.xLen
    ConfigPrinter.printParams(s"XLen:, $xLen");

  }
}
