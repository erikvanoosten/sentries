/*
 * Sentries
 * Copyright (c) 2012-2015 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the BSD 2-Clause License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

package nl.grons.sentries.core

import com.yammer.metrics.core.{Timer, MetricName}
import com.yammer.metrics.Metrics
import nl.grons.sentries.support.NotAvailableException
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.collection.JavaConverters._
import scala.util.control.Exception.ignoring

/**
 * Tests [[nl.grons.sentries.core.TimerSentry]].
 */
class TimerSentryTest extends Specification {

  "The timer sentry" should {
    "return value" in new SentryContext {
      sentry("test")("value") must_== "value"
    }

    "rethrow exception" in new SentryContext {
      sentry("test")(failing) must throwA[IllegalArgumentException]
    }

    "detect success in Metrics timer 'all'" in new SentryContext {
      sentry("successResource")(succeeding)
      registeredTimer("successResource").map(_.count()) must_== Some(1)
    }

    "detect failure in Metrics timer 'all'" in new SentryContext {
      ignoring(classOf[IllegalArgumentException])(sentry("failingResource")(failing))
      registeredTimer("failingResource").map(_.count()) must_== Some(1)
    }

    "detect non availability in Metrics timer 'all'" in new SentryContext {
      ignoring(classOf[NotAvailableException])(sentry("notAvailableResource")(notAvailable))
      registeredTimer("notAvailableResource").map(_.count()) must_== Some(1)
    }
  }

  private trait SentryContext extends Scope {
    def sentry(resourceName: String) = new TimerSentry(classOf[TimerSentryTest], resourceName)

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
      new MetricName(classOf[TimerSentryTest], resourceName + ".metrics.all")
    }
  }

}