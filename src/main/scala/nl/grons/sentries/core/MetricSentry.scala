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

import com.yammer.metrics.core.Clock
import com.yammer.metrics.Metrics
import java.util.concurrent.TimeUnit.{NANOSECONDS, SECONDS}
import nl.grons.sentries.support.{NotAvailableException, ChainableSentry}
import scala.util.control.ControlThrowable

/**
 * Sentry that collects metric of invocations.
 * A new instance can be obtained through the [[nl.grons.sentries.SentrySupport]] mixin.
 *
 * The following metrics are created:
 *
 * - timer "all" for all invocations
 * - meter "notAvailable" for invocations leading to a [[nl.grons.sentries.support.NotAvailableException]],
 *   e.g. all invocations blocked by a sentry
 * - meter "fail" for other failed invocations
 *
 * Note that in other to make effective use of the "notAvailable" meter, this sentry
 * must be the first sentry in the chain.
 *
 * This sentry can not be used in the same sentry chain as
 * the [[nl.grons.sentries.core.TimerSentry]].
 */
class MetricSentry(owner: Class[_], val resourceName: String) extends ChainableSentry {

  val sentryType = "metrics"

  private[this] val clock = Clock.defaultClock()
  private[this] val all = timerFor("all")
  private[this] val notAvailable = meterFor("notAvailable")
  private[this] val fail = meterFor("fail")

  /**
   * Run the given code block in the context of this sentry, and return its value.
   */
  def apply[T](r: => T) = {
    val start = clock.tick()
    val result = try Right(r) catch { case e: Throwable => Left(e) }
    val duration = clock.tick() - start

    all.update(duration, NANOSECONDS)
    result match {
      case Right(v) => v
      case Left(e: ControlThrowable) =>
        // Used by Scala for control, it is equivalent to success
        throw e
      case Left(e: NotAvailableException) => notAvailable.mark(); throw e
      case Left(e) => fail.mark(); throw e
    }
  }

  def reset() {}

  private def timerFor(metric: String) = Metrics.newTimer(owner, resourceName + "." + sentryType + "." + metric)
  private def meterFor(metric: String) = Metrics.newMeter(owner, resourceName + "." + sentryType + "." + metric, "invocation", SECONDS)

}
