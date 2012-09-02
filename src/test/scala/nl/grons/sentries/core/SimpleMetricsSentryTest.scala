package nl.grons.sentries.core

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import nl.grons.sentries.support.NotAvailableException
import com.yammer.metrics.core.{Timer, MetricName}
import com.yammer.metrics.Metrics
import scala.util.control.Exception.ignoring
import scala.collection.JavaConverters._

/**
 * Tests [[nl.grons.sentries.core.SimpleMetricsSentry]].
 */
class SimpleMetricsSentryTest extends Specification {

  "The simple Metric sentry" should {
    "return value" in new SentryContext {
      sentry("test")("value") must_== "value"
    }

    "detect success in Metrics timer 'all'" in new SentryContext {
      sentry("success")(succeeding)
      registeredTimer("success").map(_.count()) must_== Some(1)
    }

    "detect failure in Metrics timer 'all'" in new SentryContext {
      ignoring(classOf[IllegalArgumentException])(sentry("fail")(failing))
      registeredTimer("fail").map(_.count()) must_== Some(1)
    }

    "detect non availability in Metrics timer 'all'" in new SentryContext {
      ignoring(classOf[NotAvailableException])(sentry("notAvailable")(notAvailable))
      registeredTimer("notAvailable").map(_.count()) must_== Some(1)
    }
  }

  trait SentryContext extends Scope {
    def sentry(resourceName: String) = new MetricsSentry(resourceName, classOf[SimpleMetricsSentryTest])

    def succeeding = "fast"

    def failing: String = {
      throw new IllegalArgumentException("fail")
    }

    def notAvailable: String = {
      throw new NotAvailableException("test", "test", null)
    }

    def registeredTimer(resourceName: String): Option[Timer] = {
      Metrics.defaultRegistry().allMetrics().asScala.get(metricName(resourceName)).asInstanceOf[Option[Timer]]
    }

    def metricName(resourceName: String): MetricName = {
      new MetricName(classOf[SimpleMetricsSentryTest], resourceName + ".metrics.all")
    }
  }

}