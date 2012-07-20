/*
 * Sentries
 * Copyright (c) 2012. Erik van Oosten. All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the Revised BSD License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

package nl.grons.sentries.core

import java.util.concurrent.atomic.{AtomicLong, AtomicInteger}
import nl.grons.sentries.support.{NotAvailableException, ChainableSentry}
import com.yammer.metrics.scala.Instrumented
import com.yammer.metrics.core.Gauge

/**
 * A sentry that limits the number of invocations per time span.
 * A new instance can be obtained through the {@link Sentries} mixin.
 */
class RateLimitSentry(
  val resourceName: String,
  rate: Int,
  timeSpanMillis: Long,
  selfType: Class[_]
) extends ChainableSentry with Instrumented {

  private[this] val tokens = new AtomicInteger(rate)
  private[this] val nextTokenReleaseMillis = new AtomicLong(0L)

  val sentryType = "rateLimit"

  metricsRegistry.newGauge(selfType, constructName("available"), new Gauge[Int] {
    def value = tokens.get()
  })

  /**
   * Run the given code block in the context of this sentry, and return its value.
   *
   * When there are too many invocations in the current time span, a {@link NotAvailableException} is thrown.
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
