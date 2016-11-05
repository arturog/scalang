package scalang.epmd

import org.specs2.mutable._
import scalang._

class EpmdSpec extends SpecificationWithJUnit  with InEpmd {
  sequential

  "Epmd" should {    
    "publish a port to a running epmd instance" in {
      val epmd = Epmd("localhost")
      val creation = epmd.alive(5480, "fuck@you.com")
      creation must beLike { case Some(v : Int) => ok }
      epmd.close
      ok
    }

    "retrieve a port" in {
      val epmdPublish = Epmd("localhost")
      epmdPublish.alive(5480, "fuck@you.com")

      val epmdQuery = Epmd("localhost")
      val portPlease = epmdQuery.lookupPort("fuck@you.com")
      portPlease must beSome(5480)

      epmdPublish.close
      epmdQuery.close

      ok
    }
  }
}
