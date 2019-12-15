//
// Copyright 2012, Boundary
// 
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package scalang.node

import scalang._
import com.codahale.metrics._
import org.jetlang.fibers._
import org.jetlang.channels._
import org.jetlang.core._
import java.util.concurrent.TimeUnit
import org.cliffc.high_scale_lib.NonBlockingHashSet
import org.cliffc.high_scale_lib.NonBlockingHashMap
import scala.jdk.CollectionConverters._

import nl.grons.metrics4.scala.InstrumentedBuilder

abstract class ProcessHolder(ctx : ProcessContext) extends ProcessAdapter {
  val self = ctx.pid
  val fiber = ctx.fiber
  val messageRate = metrics.meter(s"messages.$instrumentedName")
  val executionTimer = metrics.timer(s"execution.$instrumentedName")
  def process : ProcessLike
  
  val msgChannel = new MemoryChannel[Any]
  msgChannel.subscribe(fiber, new Callback[Any] {
    def onMessage(msg : Any): Unit = {
      executionTimer.time {
        try {
          process.handleMessage(msg)
        } catch {
          case e : Throwable =>
            log.error(s"An error occurred in actor $process", e)
            process.exit(e.getMessage)
        }
      }
    }
  })
  
  val exitChannel = new MemoryChannel[(Pid,Any)]
  exitChannel.subscribe(fiber, new Callback[(Pid,Any)] {
    def onMessage(msg : (Pid,Any)): Unit = {
      try {
        process.handleExit(msg._1, msg._2)
      } catch {
        case e : Throwable =>
          log.error(s"An error occurred during handleExit in actor $this", e)
          process.exit(e.getMessage)
      }
    }
  })
  
  val monitorChannel = new MemoryChannel[(Any,Reference,Any)]
  monitorChannel.subscribe(fiber, new Callback[(Any,Reference,Any)] {
    def onMessage(msg : (Any,Reference,Any)): Unit = {
      try {
        process.handleMonitorExit(msg._1, msg._2, msg._3)
      } catch {
        case e : Throwable =>
          log.error(s"An error occurred during handleMonitorExit in actor $this", e)
          process.exit(e.getMessage)
      }
    }
  })
  
  override def handleMessage(msg : Any): Unit = {
    messageRate.mark
    msgChannel.publish(msg)
  }

  override def handleExit(from : Pid, msg : Any): Unit = {
    exitChannel.publish((from,msg))
  }

  override def handleMonitorExit(monitored : Any, ref : Reference, reason : Any): Unit = {
    monitorChannel.publish((monitored,ref,reason))
  }
  
  def cleanup: Unit = {
    fiber.dispose
    metricRegistry.remove("messages")
    metricRegistry.remove("execution")
  }
}

trait ProcessAdapter extends ExitListenable with SendListenable with LinkListenable with MonitorListenable with InstrumentedBuilder with Logging {
  override val metricRegistry = new MetricRegistry()
  
  var state = Symbol("alive")
  def self : Pid
  def fiber : Fiber
  def referenceCounter : ReferenceCounter
  val links = new NonBlockingHashSet[Link]
  val monitors = new NonBlockingHashMap[Reference, Monitor]  
  def instrumentedName = self.toErlangString
  def cleanup: Unit
  
  def handleMessage(msg : Any): Unit
  def handleExit(from : Pid, msg : Any): Unit
  def handleMonitorExit(monitored : Any, ref : Reference, reason : Any): Unit
  
  def exit(reason : Any): Unit = {
    synchronized {
      if (state != Symbol("alive")) return
      state = Symbol("dead")
    }

    // Exit listeners first, so that process is removed from table.
    for(e <- exitListeners) {
      e.handleExit(self, reason)
    }
    for (link <- links.asScala) {
      link.break(reason)
    }
    for (m <- monitors.values.asScala) {
      m.monitorExit(reason)
    }
    cleanup
  }
  
  def unlink(to : Pid): Unit = {
    links.remove(Link(self, to))
  }
  
  def link(to : Pid): Unit = {
    val l = registerLink(to)
    for (listener <- linkListeners) {
      listener.deliverLink(l)
    }
  }
  
  def registerLink(to : Pid) : Link = {
    val l = Link(self, to)
    for (listener <- linkListeners) {
      l.addLinkListener(listener)
    }
    synchronized {
      if (state != Symbol("alive"))
        l.break(Symbol("noproc"))
      else
        links.add(l)
    }
    l
  }

  def monitor(monitored : Any) : Reference = {
    val m = Monitor(self, monitored, makeRef)
    for (listener <- monitorListeners) {
      listener.deliverMonitor(m)
    }
    m.ref
  }
  
  def demonitor(ref : Reference): Unit = {
    monitors.remove(ref)
  }
  
  def registerMonitor(monitoring : Pid, ref : Reference): Monitor = {
    registerMonitor(Monitor(monitoring, self, ref))
  }

  private def registerMonitor(m : Monitor): Monitor = {
    for (listener <- monitorListeners) {
      m.addMonitorListener(listener)
    }
    synchronized {
      if (state != Symbol("alive"))
        m.monitorExit(Symbol("noproc"))
      else
        monitors.put(m.ref, m)
    }
    m
  }
  
  def makeRef = referenceCounter.makeRef
  
  def sendEvery(pid : Pid, msg : Any, delay : Long): Unit = {
    val runnable = new Runnable {
      def run = notifySend(pid,msg)
    }
    fiber.scheduleAtFixedRate(runnable, delay, delay, TimeUnit.MILLISECONDS)
  }

  def sendEvery(name : Symbol, msg : Any, delay : Long): Unit = {
    val runnable = new Runnable {
      def run = notifySend(name,msg)
    }
    fiber.scheduleAtFixedRate(runnable, delay, delay, TimeUnit.MILLISECONDS)
  }

  def sendEvery(name : (Symbol,Symbol), msg : Any, delay : Long): Unit = {
    val runnable = new Runnable {
      def run = notifySend(name,self,msg)
    }
    fiber.scheduleAtFixedRate(runnable, delay, delay, TimeUnit.MILLISECONDS)
  }

  def sendAfter(pid : Pid, msg : Any, delay : Long): Unit = {
    val runnable = new Runnable {
      def run: Unit = {
        notifySend(pid, msg)
      }
    }
    fiber.schedule(runnable, delay, TimeUnit.MILLISECONDS)
  }

  def sendAfter(name : Symbol, msg : Any, delay : Long): Unit = {
    val runnable = new Runnable {
      def run: Unit = {
        notifySend(name, msg)
      }
    }
    fiber.schedule(runnable, delay, TimeUnit.MILLISECONDS)
  }

  def sendAfter(dest : (Symbol,Symbol), msg : Any, delay : Long): Unit = {
    val runnable = new Runnable {
      def run: Unit = {
        notifySend(dest, self, msg)
      }
    }
    fiber.schedule(runnable, delay, TimeUnit.MILLISECONDS)
  }
}
