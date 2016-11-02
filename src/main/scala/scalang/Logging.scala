package scalang

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

/**
 * Defines `logger` as a lazy value initialized with an underlying `org.slf4j.Logger`
 * named according to the class into which this trait is mixed.
 */
trait Logging {

  @volatile protected lazy val log: Logger =
    Logger(LoggerFactory.getLogger(getClass.getName))
}
