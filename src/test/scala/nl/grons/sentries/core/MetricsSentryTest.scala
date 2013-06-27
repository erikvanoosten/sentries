/*
 * Sentries
 * Copyright (c) 2012-2013 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the BSD 2-Clause License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

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

  private trait SentryContext extends Scope {
    def sentry(resourceName: String) = new MetricsSentry(classOf[MetricsSentryTest], resourceName)

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
      new MetricName(classOf[MetricsSentryTest], resourceName + ".metrics.all")
    }
  }

}