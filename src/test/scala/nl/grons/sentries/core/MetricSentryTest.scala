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

import com.yammer.metrics.core.{Meter, Timer, MetricName}
import com.yammer.metrics.Metrics
import nl.grons.sentries.support.{Sentry, NotAvailableException}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.collection.JavaConverters._
import scala.util.control.Exception.ignoring

/**
 * Tests [[nl.grons.sentries.core.MetricSentry]].
 */
class MetricSentryTest extends Specification {

  "The metric sentry" should {
    "return value" in new SentryContext {
      sentry("test")("value") must_== "value"
    }

    "rethrow exception" in new SentryContext {
      sentry("test")(failing) must throwA[IllegalArgumentException]
    }

    "detect success in Metrics timer 'all'" in new SentryContext {
      sentry("successResource")(succeeding)
      registeredTimer("successResource", "all").map(_.count()) must_== Some(1)
    }

    "detect success by jumping out of closure in Metrics timer 'all'" in new SentryContext {
      succeedingByBreakingOutOfClosure(sentry("success_2_Resource")) must_== "yes"
      registeredTimer("success_2_Resource", "all").map(_.count()) must_== Some(1)
    }

    "detect failure in Metrics timer 'all' and meter 'fail'" in new SentryContext {
      ignoring(classOf[IllegalArgumentException])(sentry("failingResource")(failing))
      registeredTimer("failingResource", "all").map(_.count()) must_== Some(1)
      registeredMeter("failingResource", "fail").map(_.count()) must_== Some(1)
    }

    "detect non availability in Metrics timer 'all' and meter 'not available'" in new SentryContext {
      ignoring(classOf[NotAvailableException])(sentry("notAvailableResource")(notAvailable))
      registeredTimer("notAvailableResource", "all").map(_.count()) must_== Some(1)
      registeredMeter("notAvailableResource", "notAvailable").map(_.count()) must_== Some(1)
    }
  }

  private trait SentryContext extends Scope {
    def sentry(resourceName: String) = new MetricSentry(classOf[MetricSentryTest], resourceName)

    def succeeding = "fast"

    def succeedingByBreakingOutOfClosure(s: Sentry): String = s {
      if (1 + 1 == 2) return "yes"
      if (2 + 2 == 4) return "no"
      "no"
    }

    def failing: String = {
      throw new IllegalArgumentException("fail")
    }

    def notAvailable: String = {
      throw new NotAvailableException("test", "test", null)
    }

    def registeredTimer(resourceName: String, timerName: String): Option[Timer] = {
      Metrics.defaultRegistry().allMetrics().asScala.get(metricName(resourceName, timerName)).asInstanceOf[Option[Timer]]
    }

    def registeredMeter(resourceName: String, meterName: String): Option[Meter] = {
      Metrics.defaultRegistry().allMetrics().asScala.get(metricName(resourceName, meterName)).asInstanceOf[Option[Meter]]
    }

    def metricName(resourceName: String, timerName: String): MetricName = {
      new MetricName(classOf[MetricSentryTest], resourceName + ".metrics." + timerName)
    }
  }

}