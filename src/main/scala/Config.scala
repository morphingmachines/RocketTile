package ce

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket.{DCacheParams, ICacheParams, MulDivParams, PgLevels, RocketCoreParams}
import freechips.rocketchip.subsystem.{CacheBlockBytes, WithInclusiveCache, WithoutTLMonitors}
import freechips.rocketchip.tile.{
  //AccumulatorExample,
  BuildRoCC,
  MaxHartIdBits,
  OpcodeSet,
  RocketTileParams,
  TileKey,
  XLen,
}
import org.chipsalliance.cde.config.{Config, Field, Parameters}

class CEConfig
  extends Config((site, here, _) => {
    case XLen            => 32
    case CacheBlockBytes => (site(XLen))
    case TileKey => {
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
    case PgLevels      => if (site(XLen) == 64) 3 /* Sv39 */ else 2 /* Sv32 */
  })

class WithAccumulatorRoCCExample
  extends Config((_, _, _) => {
    case BuildRoCC => {
      // val otherRoccAcc = up(BuildRoCC)
      List { (p: Parameters) =>
        //val roccAcc = LazyModule(new AccumulatorExample(OpcodeSet.custom0)(p))
        val roccAcc = LazyModule(new AccumulatorSuperModule(OpcodeSet.custom0)(p))
        roccAcc
      } // ++ otherRoccAcc
    }
  })

case object BootROMFile extends Field[String]
class WithBootROMFile
  extends Config((site, _, _) => {
    case BootROMFile => {
      if (site(XLen) == 64)
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
class RV64Config            extends Config((new RV32Config).alterMap(Map((XLen, 64))))
class RV64WithRoCCAccConfig extends Config(new WithAccumulatorRoCCExample ++ new RV64Config)

class RV32WithL2 extends Config(new RV32Config ++ new WithL2Cache)
class RV64WithL2 extends Config(new RV64Config ++ new WithL2Cache)

class RV32WithNoTLMonitors extends Config(new RV32Config ++ new WithoutTLMonitors ++ new WithInclusiveCache)
