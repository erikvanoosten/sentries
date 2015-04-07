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

import com.yammer.metrics.Metrics
import java.util.concurrent.atomic.{AtomicLong, AtomicInteger}
import scala.concurrent.duration.FiniteDuration
import nl.grons.sentries.support.{NotAvailableException, ChainableSentry}
import nl.grons.sentries.support.MetricsSupport._

/**
 * A sentry that limits the number of invocations per time span.
 * A new instance can be obtained through the [[nl.grons.sentries.SentrySupport]] mixin.
 *
 * A rate limiter is useful for cases where you asynchronously hand of some work and you
 * don't want to overload the receiver. For example sending error emails or an
 * asynchronous rendering job for which there is a limited capacity.
 * For other cases a [[nl.grons.sentries.core.ConcurrencyLimitSentry]] is usually more
 * appropriate.
 */
class RateLimitSentry(
  owner: Class[_],
  val resourceName: String,
  rate: Int,
  timeSpan: FiniteDuration
) extends ChainableSentry {

  private[this] val timeSpanMillis = timeSpan.toMillis
  private[this] val tokens = new AtomicInteger(rate)
  private[this] val nextTokenReleaseMillis = new AtomicLong(0L)

  val sentryType = "rateLimit"

  Metrics.newGauge(owner, constructName("available"), tokens.get())

  /**
   * Run the given code block in the context of this sentry, and return its value.
   *
   * When there are too many invocations in the current time span,
   * a [[nl.grons.sentries.support.NotAvailableException]] is thrown.
   */
  def apply[T](r: => T) = {
    if (!acquireToken()) throw new RateLimitExceededException(
      resourceName, "Exceeded rate limit of " + rate + "/" + timeSpan + " for " + resourceName)
    r
  }

  private[this] def acquireToken(): Boolean = {
    val nextUpdateMillis = nextTokenReleaseMillis.get()
    val now = System.currentTimeMillis()
    if (now > nextUpdateMillis && nextTokenReleaseMillis.compareAndSet(nextUpdateMillis, now + timeSpanMillis)) {
      tokens.set(rate - 1)
      true
    } else {
      tokens.getAndDecrement > 0
    }
  }

  def reset() {
    nextTokenReleaseMillis.set(0L)
  }

  private def constructName(nameParts: String*) = (Seq(resourceName, sentryType) ++ nameParts).mkString(".")

}

/**
 * Rate limit exceeded, invocations are failing immediately.
 */
class RateLimitExceededException(resourceName: String, message: String, cause: Throwable = null)
  extends NotAvailableException(resourceName, message, cause)
