package ce

import circt.stage.ChiselStage
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.util.ElaborationArtefacts
import org.chipsalliance.cde.config

import java.io._
import java.nio.file._

trait Toplevel {
  def topModule: chisel3.RawModule
  def topModule_name = topModule.getClass().getName().split("\\$").mkString(".")

  def out_dir = s"generated_sv_dir/${topModule_name}"

  /** For firtoolOpts run `firtool --help` There is an overlap between ChiselStage args and firtoolOpts.
    *
    * TODO: Passing "--Split-verilog" "--output-annotation-file" to firtool is not working.
    */

  lazy val chiselArgs   = Array("--full-stacktrace", "--target-dir", out_dir, "--split-verilog")
  lazy val firtroolArgs = Array("-dedup")

  def chisel2firrtl() = {
    val str_firrtl = ChiselStage.emitCHIRRTL(topModule, args = Array("--full-stacktrace"))
    Files.createDirectories(Paths.get("generated_sv_dir"))
    val pw = new PrintWriter(new File(s"${out_dir}.fir"))
    pw.write(str_firrtl)
    pw.close()
  }

  // Call this only after calling chisel2firrtl()
  def firrtl2sv() =
    os.proc(
      "firtool",
      s"${out_dir}.fir",
      "--disable-annotation-unknown",
      "--split-verilog",
      "--disable-all-randomization",
      s"-o=${out_dir}",
      s"--output-annotation-file=${out_dir}/${topModule_name}.anno.json",
    ).call(stdout = os.Inherit) // check additional options with "firtool --help"

}

trait WithLazyModuleDUT { this: VerilateTestHarness with LazyToplevel =>
  override def dut            = lazyTop.module.asInstanceOf[ce.sim.TestHarnessShell]
  override def topModule_name = lazyTop.getClass().getName().split("\\$").mkString(".")
}

trait VerilateTestHarness { this: Toplevel =>
  def dut: ce.sim.TestHarnessShell

  override def topModule      = new ce.sim.TestHarness(dut)
  override def topModule_name = dut.getClass().getName().split("\\$").mkString(".")

  val rocketchip_resource_path = s"${os.pwd.toString()}/../playground/dependencies/rocket-chip/src/main/resources"

  val extra_rtl_src = Seq("/vsrc/EICG_wrapper.v").map(i => rocketchip_resource_path + i)
  // riscv-tools installation path
  val riscv_tools_path = sys.env.get("RISCV") match {
    case Some(value) => value
    case None        => throw new Exception("Environment variable \"RISCV\" is no defined!")
  }

  def CFLAGS(extra_flags: Seq[String]): Seq[String] = {
    val default = Seq("-std=c++11", "-DVERILATOR", s"-I${riscv_tools_path}/include")
    val opts    = default ++ extra_flags
    opts.map(i => Seq("-CFLAGS", i)).flatten
  }

  def LDFLAGS(extra_flags: Seq[String]): Seq[String] = {
    val default = Seq(s"-L${riscv_tools_path}/lib", "-lfesvr", "-lriscv", "-lpthread")
    val opts    = default ++ extra_flags
    opts.map(i => Seq("-LDFLAGS", i)).flatten
  }

  def verilate(
    extra_CFLAGS:  Seq[String] = Seq(),
    extra_LDFLAGS: Seq[String] = Seq(),
    extras_src:    Seq[String] = Seq(),
  ) = {
    val cmd = Seq("verilator", "-Wno-LATCH", "--cc", "-CFLAGS", "-std=c++11") ++ CFLAGS(extra_CFLAGS) ++ LDFLAGS(
      extra_LDFLAGS,
    ) ++
      extras_src ++ extra_rtl_src ++
      Seq(
        "-f",
        "filelist.f",
        "--top-module",
        "TestHarness",
        "--trace",
        "--vpi",
        "--exe",
        s"${os.pwd.toString()}/src/main/resources/test_tb_top.cpp",
      )
    os.proc(cmd).call(cwd = os.Path(s"${os.pwd.toString()}/${out_dir}"), stdout = os.Inherit)

  }

  def build() = {
    val cmd = Seq("make", "-j", "-C", "obj_dir/", "-f", s"VTestHarness.mk")
    os.proc(cmd).call(cwd = os.Path(s"${os.pwd.toString()}/${out_dir}"), stdout = os.Inherit)
    println(s"VTestHarness executable in ./generated_sv_dir/${topModule_name}/obj_dir directory.")
    println(s"Run simulation using: ./VTestHarness <rv32IMA>.elf")
  }
}

trait LazyToplevel extends Toplevel {
  def lazyTop: LazyModule
  override def topModule      = lazyTop.module.asInstanceOf[chisel3.RawModule]
  override def topModule_name = lazyTop.getClass().getName().split("\\$").mkString(".")

  def genDiplomacyGraph() = {
    ElaborationArtefacts.add("graphml", lazyTop.graphML)
    Files.createDirectories(Paths.get(out_dir))
    ElaborationArtefacts.files.foreach {
      case ("graphml", graphML) =>
        val fw = new FileWriter(new File(s"${out_dir}", s"${lazyTop.className}.graphml"))
        fw.write(graphML())
        fw.close()
      case _ =>
    }
  }

  def showModuleComposition(gen: => LazyModule) = {
    println("List of Diplomatic Nodes (Ports)")
    gen.getNodes.map(x => println(s"Class Type:  ${x.getClass.getName()} | node: ${x.name} (${x.description})"))
    println("")
    println("List of Sub Modules")
    // def hierarchyName(x: => LazyModule) :String = {
    //   x.parents.map(_.name).foldRight(".")(_ + _)
    // }
    gen.getChildren.map(x => println("Class Type: " + x.getClass.getName() + "| Instance name:" + x.name))
  }

}

/** To run from a terminal shell
  * {{{
  * mill ce.runMain ce.ceMain CE
  * }}}
  */

object ceMain extends App with LazyToplevel {
  import org.chipsalliance.cde.config.Parameters
  val str = if (args.length == 0) "" else args(0)
  val lazyTop = str match {
    case "CE"  => LazyModule(new ce.CeTop)
    case "Sim" => LazyModule(new ce.sim.SimMemory()(Parameters.empty))
    case "DUT" => LazyModule(new ce.sim.SimDUT()(Parameters.empty))
    case _     => LazyModule(new ce.CeTop)
    // case _    => throw new Exception("Unknown Module Name!")
  }

  showModuleComposition(lazyTop)

  chisel2firrtl()
  firrtl2sv()
  genDiplomacyGraph()

}

object TestLazyMain extends App with LazyToplevel with VerilateTestHarness with WithLazyModuleDUT {
  lazy val lazyTop = LazyModule(new ce.sim.SimDUT()(config.Parameters.empty))

  chisel2firrtl()
  genDiplomacyGraph()
  firrtl2sv()
  verilate()
  build()
}

object TestMain extends App with Toplevel with VerilateTestHarness {
  lazy val dut = new ce.sim.DUT

  chisel2firrtl()
  firrtl2sv()
  verilate()
  build()
}
