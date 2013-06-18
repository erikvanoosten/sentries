/*
 * Sentries
 * Copyright (c) 2012-2013 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the BSD 2-Clause License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

package nl.grons.sentries.support

import nl.grons.sentries.core._
import nl.grons.sentries.cross.Concurrent._

/**
 * Lots of code to make creating sentries trivially easy.
 *
 * For usage instructions see [[nl.grons.sentries.SentrySupport]].
 */
abstract class SentryBuilder(owner: Class[_], val resourceName: String, sentryRegistry: SentriesRegistry) {

  /**
   * Append a metrics sentry to the current sentry.
   *
   * One timer is registered: "all". It is update for each invocation.
   * For more extensive measuring, use [[.withFullMetrics]] instead.
   *
   * @return a new sentry that collects metrics after the current sentry behavior
   */
  def withMetrics: ChainableSentry with SentryBuilder =
    withSentry(new MetricsSentry(resourceName, owner))

  /**
   * Append an extensive metrics sentry to the current sentry.
   *
   * Four timers are registered: "all", "success", "fail" and "notAvailable". These are update for respectively
   * each invocation, succeeding invocations, invocations that throw an exception, and invocations that are blocked
   * by a sentry that is later in the chain (detected by catching
   * [[nl.grons.sentries.support.NotAvailableException NotAvailableException]]s).
   *
   * When 4 timers is too much detail, use [[.withMetrics]] instead.
   *
   * @return a new sentry that collects metrics after the current sentry behavior
   */
  def withFullMetrics: ChainableSentry with SentryBuilder =
    withSentry(new FullMetricsSentry(resourceName, owner))

  /**
   * Append a circuit breaker sentry to the current sentry.
   *
   * @param failLimit number of failure after which the flow will be broken
   * @param retryDelay timeout for trying again
   * @return a new sentry that applies a circuit breaker after the current sentry behavior
   */
  def withFailLimit(failLimit: Int, retryDelay: Duration): ChainableSentry with SentryBuilder =
    withSentry(new CircuitBreakerSentry(resourceName, failLimit, retryDelay, owner))

  /**
   * Append an adaptive throughput sentry to the current sentry.
   * See [[nl.grons.sentries.core.AdaptiveThroughputSentry AdaptiveThroughputSentry]] for more information.
   *
   * @param targetSuccessRatio target success ratio, `0 < targetSuccessRatio < 1`, defaults to 0.95
   * @return a new sentry that adaptively changes allowed throughput on top of the current sentry behavior
   */
  def withAdaptiveThroughput(targetSuccessRatio: Double = 0.95D): ChainableSentry with SentryBuilder =
    withSentry(new AdaptiveThroughputSentry(resourceName, targetSuccessRatio, owner))

  /**
   * Append a concurrency limit sentry to the current sentry.
   *
   * @param concurrencyLimit number of concurrently allowed invocations
   * @return a new sentry that applies a concurrency limit after the current sentry behavior
   */
  def withConcurrencyLimit(concurrencyLimit: Int): ChainableSentry with SentryBuilder =
    withSentry(new ConcurrencyLimitSentry(resourceName, concurrencyLimit, owner))

  /**
   * Append a rate limit sentry to the current sentry.
   *
   * @param rate number of invocations per time unit
   * @param per the time unit in millis
   * @return a new sentry that applies a concurrency limit after the current sentry behavior
   */
  def withRateLimit(rate: Int, per: Long): ChainableSentry with SentryBuilder =
    withSentry(new RateLimitSentry(resourceName, rate, per, owner))

  /**
   * Append a invocation duration limit sentry to the current sentry.
   *
   * WARNING: do NOT use this sentry when you invoke it from 1) a [[akka.dispatch.Future]] or 2) an [[akka.actor.Actor]].
   * For such circumstances you are MUCH better of with a timeout on the enclosing future or a timeout message
   * within the actor.
   * Reason: this sentry blocks the current thread while waiting on a future that executes the task. Blocking the
   * current thread is an anti-pattern for futures and actors.
   *
   * @param durationLimit the maximum duration of a call
   * @return a new sentry that applies a duration limit after the current sentry behavior
   */
  def withDurationLimit(durationLimit: Duration): ChainableSentry with SentryBuilder =
    withSentry(new DurationLimitSentry(resourceName, durationLimit))

  /**
   * Append a custom sentry to the current sentry.
   *
   * @param andThenSentry the sentry to add
   * @return a new sentry that applies the given sentry after the current sentry behavior
   */
  def withSentry(andThenSentry: ChainableSentry): ChainableSentry with SentryBuilder

  /** Returns the registered instance of the sentry (when applicable). */
  protected def registered(sentry: ChainableSentry): ChainableSentry =
    // Only register sentries with a sentryType.
    if (sentry.sentryType == null)
      sentry
    else
      sentryRegistry.getOrAdd[ChainableSentry](sentry, owner, resourceName, sentry.sentryType)
}

class InitialSentryBuilder(owner: Class[_], resourceName: String, sentryRegistry: SentriesRegistry)
  extends SentryBuilder(owner, resourceName, sentryRegistry) {

  def withSentry(sentry: ChainableSentry) =
    new ComposingSentryBuilder(owner, resourceName, sentryRegistry, registered(sentry))
}

class ComposingSentryBuilder(
    owner: Class[_], resourceName: String, sentryRegistry: SentriesRegistry, val sentry: Sentry)
  extends SentryBuilder(owner, resourceName, sentryRegistry) with ChainableSentry {

  def withSentry(composeSentry: ChainableSentry) =
    new ComposingSentryBuilder(owner, resourceName, sentryRegistry, sentry compose registered(composeSentry))

  def apply[T](r: => T) = sentry(r)

  def reset() {
    sentry.reset()
  }

  def sentryType = null
}
