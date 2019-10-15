package scalang.node

import org.jboss.netty
import netty.bootstrap._
import netty.channel._
import netty.handler.codec.frame._
import com.codahale.metrics._
import java.util.concurrent._
import nl.grons.metrics4.scala.InstrumentedBuilder

class PacketCounter(name : String) extends SimpleChannelHandler with InstrumentedBuilder {
  override val metricRegistry = new MetricRegistry()

  val ingress = metrics.meter("ingress", "packets")
  val egress = metrics.meter("egress", "packets")
  val exceptions = metrics.meter("exceptions", "exceptions")

  override def messageReceived(ctx : ChannelHandlerContext, e : MessageEvent) {
    ingress.mark
    super.messageReceived(ctx, e)
  }

  override def exceptionCaught(ctx : ChannelHandlerContext, e : ExceptionEvent) {
     exceptions.mark
     super.exceptionCaught(ctx, e)
  }

  override def writeRequested(ctx : ChannelHandlerContext, e : MessageEvent) {
    egress.mark
    super.writeRequested(ctx, e)
  }
}
