/*
 * Sentries
 * Copyright (c) 2012-2015 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the BSD 2-Clause License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

package nl.grons.sentries.support

import java.util.concurrent.TimeUnit
import nl.grons.sentries.core._
import scala.concurrent.duration.{FiniteDuration, Duration}

/**
 * Lots of code to make creating sentries trivially easy.
 *
 * For usage instructions see [[nl.grons.sentries.SentrySupport]].
 */
abstract class SentryBuilder(owner: Class[_], val resourceName: String, sentryRegistry: SentriesRegistry) {

  /**
   * Append a metric sentry to the current sentry.
   *
   * One timer and 2 meter metrics are registered: "all", "fail" and "notAvailable".
   * The "all" timer times and counts all invocations, the "fail" meter counts invocations that
   * threw an exception, and the "notAvailable" meter counts invocations that were blocked
   * by a sentry that is later in the chain (detected by catching
   * [[nl.grons.sentries.support.NotAvailableException]]s).
   *
   * When only a timer is needed, use [[.withTimer]] instead.
   *
   * To count the 'not available' invocations, this must be the first sentry in
   * the chain.
   *
   * For more information see [[nl.grons.sentries.core.MetricSentry]].
   *
   * @return a new sentry that collects metrics after the current sentry behavior
   */
  def withMetrics: ChainableSentry with SentryBuilder =
    withSentry(new MetricSentry(owner, resourceName))

  /**
   * Append a timer sentry to the current sentry.
   *
   * One timer is registered: "all". It is updated for each invocation.
   * For more extensive measuring, use [[.withMetrics]] instead.
   *
   * For more information see [[nl.grons.sentries.core.TimerSentry]].
   *
   * @return a new sentry that collects metrics after the current sentry behavior
   */
  def withTimer: ChainableSentry with SentryBuilder =
    withSentry(new TimerSentry(owner, resourceName))

  /**
   * Append a circuit breaker sentry to the current sentry.
   * See [[nl.grons.sentries.core.CircuitBreakerSentry]] for more information.
   *
   * @param failLimit number of failure after which the flow will be broken
   * @param retryDelay timeout for trying again, defaults to 1 second
   * @return a new sentry that applies a circuit breaker after the current sentry behavior
   */
  def withFailLimit(failLimit: Int, retryDelay: FiniteDuration = Duration(1, TimeUnit.SECONDS)): ChainableSentry with SentryBuilder =
    withSentry(new CircuitBreakerSentry(owner, resourceName, failLimit, retryDelay))

  /**
   * Append an adaptive throughput sentry to the current sentry.
   * See [[nl.grons.sentries.core.AdaptiveThroughputSentry]] for more information.
   *
   * @param targetSuccessRatio target success ratio, `0 < targetSuccessRatio < 1`, defaults to 0.95
   * @param evaluationDelay the time between calculations of the current throughput, defaults to 1 second
   * @param minimumInvocationCountThreshold the minimum number of invocations that must be observed per `evaluationDelay`
   *   before invocations are throttled, defaults to `0` (>=0)
   * @param successIncreaseFactor factor to apply to current throughput ratio, `successIncreaseFactor > 1`, defaults to 1.2D
   * @param failedInvocationDurationThreshold the minimum duration for a failed invocation to be counted as failed,
   *   defaults to 300 nanoseconds
   * @return a new sentry that adaptively changes allowed throughput after the current sentry behavior
   */
  def withAdaptiveThroughput(
      targetSuccessRatio: Double = 0.95D,
      evaluationDelay: FiniteDuration = Duration(1, TimeUnit.SECONDS),
      minimumInvocationCountThreshold: Int = 0,
      successIncreaseFactor: Double = 1.2D,
      failedInvocationDurationThreshold: FiniteDuration = Duration(300, TimeUnit.NANOSECONDS)
  ): ChainableSentry with SentryBuilder =
    withSentry(new AdaptiveThroughputSentry(owner, resourceName, targetSuccessRatio, evaluationDelay, minimumInvocationCountThreshold, successIncreaseFactor, failedInvocationDurationThreshold))

  /**
   * Append a circuit breaker AND an adaptive throughput sentry to the current sentry.
   * See [[nl.grons.sentries.core.CircuitBreakerSentry]] and
   * [[nl.grons.sentries.core.AdaptiveThroughputSentry]] for more information.
   *
   * The `failedInvocationDurationThreshold` is set to 300 nanoseconds.
   *
   * @param failLimit number of failure after which the flow will be broken by circuit breaker, AND the minimum
   *   number of invocations before throttling takes place in the adaptive throughput
   * @param retryDelay timeout for trying again (> 5 milliseconds), defaults to 1 second
   * @param targetSuccessRatio target success ratio for adaptive throughput, `0 < targetSuccessRatio < 1`, defaults to 0.95
   * @return a new sentry that applies adaptive throughput after a circuit breaker after the current sentry behavior
   */
  def withFailLimitAndAdaptiveThroughput(
    failLimit: Int,
    retryDelay: FiniteDuration = Duration(1, TimeUnit.SECONDS),
    targetSuccessRatio: Double = 0.95D
  ): ChainableSentry with SentryBuilder = {
    require(retryDelay.toMillis > 5, "retryDelay must be longer then 5 milliseconds")
    // The adaptive throughput's delay is slightly shortened so that retries from circuit breaker coincide with
    // retries in adaptive throughput sentry.
    withFailLimit(failLimit, retryDelay).
      withAdaptiveThroughput(targetSuccessRatio, retryDelay - Duration(5, TimeUnit.MILLISECONDS), failLimit,
                             failedInvocationDurationThreshold = Duration(300, TimeUnit.NANOSECONDS))
  }

  /**
   * Append a concurrency limit sentry to the current sentry.
   *
   * @param concurrencyLimit number of concurrently allowed invocations
   * @return a new sentry that applies a concurrency limit after the current sentry behavior
   */
  def withConcurrencyLimit(concurrencyLimit: Int): ChainableSentry with SentryBuilder =
    withSentry(new ConcurrencyLimitSentry(owner, resourceName, concurrencyLimit))

  /**
   * Append a rate limit sentry to the current sentry.
   *
   * @param rate number of invocations per time unit
   * @param per the time unit
   * @return a new sentry that applies a concurrency limit after the current sentry behavior
   */
  def withRateLimit(rate: Int, per: FiniteDuration): ChainableSentry with SentryBuilder =
    withSentry(new RateLimitSentry(owner, resourceName, rate, per))

  /**
   * Append a invocation duration limit sentry to the current sentry.
   *
   * WARNING: do NOT use this sentry when you invoke it from a `Future` or an `Actor`.
   * For such circumstances you are MUCH better of with a timeout on the enclosing future or a timeout message
   * within the actor.
   * Reason: this sentry blocks the current thread while waiting on a future that executes the task. Blocking the
   * current thread is an anti-pattern for futures and actors.
   *
   * @param durationLimit the maximum duration of a call
   * @return a new sentry that applies a duration limit after the current sentry behavior
   */
  def withDurationLimit(durationLimit: FiniteDuration): ChainableSentry with SentryBuilder =
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
