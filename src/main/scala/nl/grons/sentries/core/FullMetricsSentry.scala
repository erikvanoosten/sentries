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

import nl.grons.sentries.support.{NotAvailableException, ChainableSentry}
import com.yammer.metrics.core.Clock
import com.yammer.metrics.Metrics
import java.util.concurrent.TimeUnit.NANOSECONDS
import scala.util.control.ControlThrowable

/**
 * Sentry that collects metric of invocations.
 * A new instance can be obtained through the [[nl.grons.sentries.SentrySupport]] mixin.
 */
class FullMetricsSentry(owner: Class[_], val resourceName: String) extends ChainableSentry {

  val sentryType = "metrics"

  private[this] val clock = Clock.defaultClock()
  private[this] val all = metricFor("all")
  private[this] val success = metricFor("success")
  private[this] val notAvailable = metricFor("notAvailable")
  private[this] val fail = metricFor("fail")

  /**
   * Run the given code block in the context of this sentry, and return its value.
   */
  def apply[T](r: => T) = {
    val start = clock.tick()
    val result = try Right(r) catch { case e: Throwable => Left(e) }
    val duration = clock.tick() - start

    all.update(duration, NANOSECONDS)
    result match {
      case Right(v) => success.update(duration, NANOSECONDS); v
      case Left(e: ControlThrowable) => success.update(duration, NANOSECONDS); throw e
      case Left(e: NotAvailableException) => notAvailable.update(duration, NANOSECONDS); throw e
      case Left(e) => fail.update(duration, NANOSECONDS); throw e
    }
  }

  def reset() {}

  private def metricFor(metric: String) = Metrics.newTimer(owner, resourceName + "." + sentryType + "." + metric)

}
