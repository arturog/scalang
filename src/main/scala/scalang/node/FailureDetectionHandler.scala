package scalang.node

import org.jboss.{netty => netty}
import netty.channel._
import netty.util._
import netty.handler.timeout._
import java.util.concurrent._
import scalang.Logging

class FailureDetectionHandler(node : Symbol, clock : Clock, tickTime : Int, timer : Timer) extends SimpleChannelHandler with Logging {
  @volatile var nextTick : Timeout = null
  @volatile var lastTimeReceived = 0L
  @volatile var ctx : ChannelHandlerContext = null
  val exception = new ReadTimeoutException

  override def channelOpen(ctx : ChannelHandlerContext, e : ChannelStateEvent): Unit = {
    this.ctx = ctx
    lastTimeReceived = clock.currentTimeMillis
    scheduleTick
  }

  override def channelClosed(ctx : ChannelHandlerContext, e : ChannelStateEvent): Unit = {
    if (nextTick != null) nextTick.cancel
  }

  override def messageReceived(ctx : ChannelHandlerContext, e : MessageEvent): Unit = {
    lastTimeReceived = clock.currentTimeMillis
    e.getMessage match {
      case Tick =>
        if (nextTick != null) nextTick.cancel
        ctx.getChannel.write(Tock)
      case _ =>
        ctx.sendUpstream(e);
    }
  }

  object TickTask extends TimerTask {
    override def run(timeout : Timeout): Unit = {
      val last = (clock.currentTimeMillis - lastTimeReceived) / 1000
      if (last > (tickTime - tickTime/4)) {
        log.warn(s"Connection to $node has failed for $last seconds. Closing the connection.")
        Channels.fireExceptionCaught(ctx, exception);
      }
      ctx.getChannel.write(Tick)
      scheduleTick
    }
  }

  def scheduleTick: Unit = {
    nextTick = timer.newTimeout(TickTask, tickTime / 4, TimeUnit.SECONDS)
  }
}
