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
import java.util.concurrent.Semaphore
import nl.grons.sentries.support.{NotAvailableException, ChainableSentry}
import nl.grons.sentries.support.MetricsSupport._

/**
 * A sentry that limits the number of concurrent invocations.
 * A new instance can be obtained through the [[nl.grons.sentries.SentrySupport]] mixin.
 *
 * The goal of a concurrency limiter is to prevent overloading of a shared resource.
 * This sentry can be used as an alternative to a pool for easy to crate objects.
 */
class ConcurrencyLimitSentry(
  owner: Class[_],
  val resourceName: String,
  concurrencyLimit: Int
) extends ChainableSentry {

  val sentryType = "concurrencyLimit"

  // Note: as we only use tryAcquire, no fairness is necessary.
  private[this] val semaphore = new Semaphore(concurrencyLimit, false)

  Metrics.newGauge(owner, constructName("available"), semaphore.availablePermits())

  /**
   * Run the given code block in the context of this sentry, and return its value.
   *
   * When there are too many concurrent invocations, a [[nl.grons.sentries.support.NotAvailableException]] is thrown.
   */
  def apply[T](r: => T) = {
    if (!semaphore.tryAcquire()) throw new ConcurrencyLimitExceededException(
      resourceName, "Exceeded concurrency limit of " + concurrencyLimit + " for " + resourceName)

    try r finally semaphore.release()
  }

  def reset() {}

  private def constructName(nameParts: String*) = (Seq(resourceName, sentryType) ++ nameParts).mkString(".")

}

/**
 * Concurrency limit exceeded, invocations are failing immediately.
 */
class ConcurrencyLimitExceededException(resourceName: String, message: String, cause: Throwable = null) extends NotAvailableException(resourceName, message, cause)
