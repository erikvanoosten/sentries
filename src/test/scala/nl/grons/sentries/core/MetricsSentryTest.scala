package nl.grons.sentries.core

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import nl.grons.sentries.support.NotAvailableException
import com.yammer.metrics.core.{Timer, MetricName}
import com.yammer.metrics.Metrics
import scala.util.control.Exception.ignoring
import scala.collection.JavaConverters._

/**
 * Tests [[nl.grons.sentries.core.MetricsSentry]].
 */
class MetricsSentryTest extends Specification {

  "The Metric sentry" should {
    "return value" in new SentryContext {
      sentry("test")("value") must_== "value"
    }

    "detect success in Metrics timers 'all' and 'success'" in new SentryContext {
      sentry("success")(succeeding)
      registeredTimer("success", "all").map(_.count()) must_== Some(1)
      registeredTimer("success", "success").map(_.count()) must_== Some(1)
    }

    "detect failure in Metrics timers 'all' and 'fail'" in new SentryContext {
      ignoring(classOf[IllegalArgumentException])(sentry("fail")(failing))
      registeredTimer("fail", "all").map(_.count()) must_== Some(1)
      registeredTimer("fail", "fail").map(_.count()) must_== Some(1)
    }

    "detect non availability in Metrics timers 'all' and 'not available'" in new SentryContext {
      ignoring(classOf[NotAvailableException])(sentry("notAvailable")(notAvailable))
      registeredTimer("notAvailable", "all").map(_.count()) must_== Some(1)
      registeredTimer("notAvailable", "notAvailable").map(_.count()) must_== Some(1)
    }
  }

  trait SentryContext extends Scope {
    def sentry(resourceName: String) = new MetricsSentry(resourceName, classOf[MetricsSentryTest])

    def succeeding = "fast"

    def failing: String = {
      throw new IllegalArgumentException("fail")
    }

    def notAvailable: String = {
      throw new NotAvailableException("test", "test", null)
    }

    def registeredTimer(resourceName: String, timerName: String): Option[Timer] = {
      Metrics.defaultRegistry().allMetrics().asScala.get(metricName(resourceName, timerName)).asInstanceOf[Option[Timer]]
    }

    def metricName(resourceName: String, timerName: String): MetricName = {
      new MetricName(classOf[MetricsSentryTest], resourceName + ".metrics." + timerName)
    }
  }

}