//
// Copyright 2011, Boundary
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

import nl.grons.metrics4.scala.InstrumentedBuilder

trait ProcessLike extends InstrumentedBuilder with Logging {
  override val metricRegistry = new MetricRegistry()
  def adapter : ProcessAdapter
  def self : Pid
  
  def send(pid : Pid, msg : Any) = adapter.notifySend(pid,msg)
  def send(name : Symbol, msg : Any) = adapter.notifySend(name,msg)
  def send(dest : (Symbol,Symbol), from : Pid, msg : Any) = adapter.notifySend(dest,from,msg)
  
  def handleMessage(msg : Any): Unit
  
  def handleExit(from : Pid, reason : Any): Unit = {
    exit(reason)
  }

  def handleMonitorExit(monitored : Any, ref : Reference, reason : Any): Unit
  
  def exit(reason : Any): Unit = {
    adapter.exit(reason)
  }
  
  def makeRef = adapter.makeRef

  def unlink(to : Pid): Unit = {
    adapter.unlink(to)
  }
  
  def link(to : Pid): Unit = {
    adapter.link(to)
  }

  def monitor(monitored : Any): Reference = {
    adapter.monitor(monitored)
  }
  
  def demonitor(ref : Reference): Unit = {
    adapter.demonitor(ref)
  }
}
