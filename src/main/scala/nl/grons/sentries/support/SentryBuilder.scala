/*
 * Sentries
 * Copyright (c) 2012 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the Revised BSD License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

package nl.grons.sentries.support

import nl.grons.sentries.core._

/**
 * Lots of code to make creating sentries trivially easy.
 */
abstract class SentryBuilder(owner: Class[_], val resourceName: String, sentryRegistry: SentriesRegistry) {

  /**
   * Append a circuit breaker sentry to the current sentry.
   *
   * @param failLimit number of failure after which the flow will be broken
   * @param retryDelayMillis timeout for trying again
   * @return a new sentry that applies a circuit breaker after the current sentry behavior
   */
  def withFailLimit(failLimit: Int, retryDelayMillis: Long): NamedSentry with SentryBuilder =
    withSentry(new CircuitBreakerSentry(resourceName, failLimit, retryDelayMillis, owner))

  /**
   * Append a concurrency limit sentry to the current sentry.
   *
   * @param concurrencyLimit number of concurrently allowed invocations
   * @return a new sentry that applies a concurrency limit after the current sentry behavior
   */
  def withConcurrencyLimit(concurrencyLimit: Int): NamedSentry with SentryBuilder =
    withSentry(new ConcurrencyLimitSentry(resourceName, concurrencyLimit, owner))

  /**
   * Append a rate limit sentry to the current sentry.
   *
   * @param rate number of invocations per time unit
   * @param per the time unit in millis
   * @return a new sentry that applies a concurrency limit after the current sentry behavior
   */
  def withRateLimit(rate: Int, per: Long): NamedSentry with SentryBuilder =
    withSentry(new RateLimitSentry(resourceName, rate, per, owner))

  /**
   * Append a invocation duration limit sentry to the current sentry.
   *
   * Note: in the Scala 2.9.1 build this sentry uses Akka Futures.
   *
   * @param durationLimitMillis the maximum duration of a call in millis
   * @return a new sentry that applies a duration limit after the current sentry behavior
   */
  def withDurationLimit(durationLimitMillis: Long): NamedSentry with SentryBuilder =
    withSentry(new DurationLimitSentry(resourceName, durationLimitMillis))

  /**
   * Append a custom sentry to the current sentry.
   *
   * @param andThenSentry the sentry to add
   * @return a new sentry that applies the given sentry after the current sentry behavior
   */
  def withSentry(andThenSentry: ChainableSentry): NamedSentry with SentryBuilder
}

class InitialSentryBuilder(owner: Class[_], resourceName: String, sentryRegistry: SentriesRegistry)
  extends SentryBuilder(owner, resourceName, sentryRegistry) {

  def withSentry(sentry: ChainableSentry) = {
    val s = sentryRegistry.getOrAdd[ChainableSentry](sentry, owner, resourceName, sentry.sentryType)
    new ComposingSentryBuilder(owner, resourceName, sentryRegistry, s)
  }
}

class ComposingSentryBuilder(
    owner: Class[_], resourceName: String, sentryRegistry: SentriesRegistry, val sentry: Sentry)
  extends SentryBuilder(owner, resourceName, sentryRegistry) with NamedSentry {

  def withSentry(andThenSentry: ChainableSentry) = {
    val s = sentryRegistry.getOrAdd[ChainableSentry](andThenSentry, owner, resourceName, andThenSentry.sentryType)
    new ComposingSentryBuilder(owner, resourceName, sentryRegistry, sentry andThen s)
  }

  def apply[T](r: => T) = sentry(r)

  def reset() {
    sentry.reset()
  }
}
