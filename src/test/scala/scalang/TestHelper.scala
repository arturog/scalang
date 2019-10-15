package scalang

import java.lang.ProcessBuilder
import java.lang.{Process => SysProcess}
import java.io._
import scala.collection.JavaConverters._
import scala.collection.mutable.StringBuilder
import org.specs2.specification.BeforeAfterAll

object ErlangVM {
  def apply(name : String, cookie : String, eval : Option[String]) : SysProcess = {
    val commands = List("erl", "-sname", name, "-setcookie", cookie, "-noshell", "-smp") ++
      (for (ev <- eval) yield {
        List("-eval", ev)
      }).getOrElse(Nil)
    val builder = new ProcessBuilder(commands.asJava)
    builder.start
  }
}

object Escript {
  def apply(command : String, args : String*) : SysProcess = {
    val url = getClass.getClassLoader.getResource(command)
    val file = new File(url.getFile)
    file.setExecutable(true)
    val builder = new ProcessBuilder((List(url.getFile) ++ args.toList).asJava)
    builder.start
  }
}

object EpmdCmd {
  def apply() : SysProcess = {
    val builder = new ProcessBuilder("epmd")
    builder.start
  }
}

trait InEpmd extends BeforeAfterAll {
  var epmd : SysProcess = null  
   
  def beforeAll() {
    epmd = EpmdCmd()
  }
  
  def afterAll() {
    epmd.destroy
    epmd.waitFor
  }
}

object ReadLine {
  def apply(proc : SysProcess) : String = {
    val read = new BufferedReader(new InputStreamReader(proc.getInputStream))
    val line = read.readLine
    if(line == null) {
      throw new RuntimeException("error getting result from escript. ensure that erlang is installed and available on the path.")
    }
    line
  }
}
