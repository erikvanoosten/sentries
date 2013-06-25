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

import com.yammer.metrics.Metrics
import java.util.concurrent.atomic.{AtomicLong, AtomicInteger}
import nl.grons.sentries.support.{NotAvailableException, ChainableSentry}
import nl.grons.sentries.support.MetricsSupport._

/**
 * A sentry that limits the number of invocations per time span.
 * A new instance can be obtained through the [[nl.grons.sentries.SentrySupport]] mixin.
 */
class RateLimitSentry(
  val resourceName: String,
  rate: Int,
  timeSpanMillis: Long,
  owner: Class[_]
) extends ChainableSentry {

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
      resourceName, "Exceeded rate limit of " + rate + "/" + timeSpanMillis + "ms for " + resourceName)
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
