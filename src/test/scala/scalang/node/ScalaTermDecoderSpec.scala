package scalang.node

import org.specs2.mutable._
import org.jboss.netty.buffer.ChannelBuffers
import java.nio.charset.Charset

class ScalaTermDecoderSpec extends SpecificationWithJUnit {
  "ScalaTermDecoder" should {
    "decode a string" in {
      val buffer = ChannelBuffers.copiedBuffer("abc", Charset.forName("utf-8"))
      val decoded = ScalaTermDecoder.fastString(buffer, 3)
      decoded must be_==("abc")
      decoded.length must be_==(3)
    }
  }
}
