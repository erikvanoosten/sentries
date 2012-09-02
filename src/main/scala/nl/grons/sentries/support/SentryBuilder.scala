/*
 * Sentries
 * Copyright (c) 2012 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the BSD 2-Clause License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

package nl.grons.sentries.support

import nl.grons.sentries.core._

/**
 * Lots of code to make creating sentries trivially easy.
 *
 * For usage instructions see [[nl.grons.sentries.SentrySupport SentrySupport]].
 */
abstract class SentryBuilder(owner: Class[_], val resourceName: String, sentryRegistry: SentriesRegistry) {

  /**
   * Append a metrics sentry to the current sentry.
   *
   * Four timers are registered: "all", "success", "fail" and "notAvailable". These are update for respectively
   * each invocation, succeeding invocations, invocations that throw an exception, and invocations that are blocked
   * by a sentry that is later in the chain (detected by catching [[nl.grons.sentries.support.NotAvailableException]]s).
   *
   * When 4 timers is too much detail, use [[.withSimpleMetrics]] instead.
   *
   * @return a new sentry that collects metrics after the current sentry behavior
   */
  def withMetrics: ChainableSentry with SentryBuilder =
    withSentry(new MetricsSentry(resourceName, owner))

  /**
   * Append a metrics sentry to the current sentry.
   *
   * One timer is registered: "all". It is update for each invocation. For more information, use [[.withMetrics]]
   * instead.
   *
   * @return a new sentry that collects metrics after the current sentry behavior
   */
  def withSimpleMetrics: ChainableSentry with SentryBuilder =
    withSentry(new SimpleMetricsSentry(resourceName, owner))

  /**
   * Append a circuit breaker sentry to the current sentry.
   *
   * @param failLimit number of failure after which the flow will be broken
   * @param retryDelayMillis timeout for trying again
   * @return a new sentry that applies a circuit breaker after the current sentry behavior
   */
  def withFailLimit(failLimit: Int, retryDelayMillis: Long): ChainableSentry with SentryBuilder =
    withSentry(new CircuitBreakerSentry(resourceName, failLimit, retryDelayMillis, owner))

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
   * @param durationLimitMillis the maximum duration of a call in millis
   * @return a new sentry that applies a duration limit after the current sentry behavior
   */
  def withDurationLimit(durationLimitMillis: Long): ChainableSentry with SentryBuilder =
    withSentry(new DurationLimitSentry(resourceName, durationLimitMillis))

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
