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
import nl.grons.sentries.support.{Sentry, NotAvailableException}
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

    "detect success by jumping out of closure in Metrics timers 'all' and 'success'" in new SentryContext {
      succeedingByBreakingOutOfClosure(sentry("success_2")) must_== "yes"
      registeredTimer("success_2", "all").map(_.count()) must_== Some(1)
      registeredTimer("success_2", "success").map(_.count()) must_== Some(1)
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

    def metricName(resourceName: String, timerName: String): MetricName = {
      new MetricName(classOf[MetricsSentryTest], resourceName + ".metrics." + timerName)
    }
  }

}