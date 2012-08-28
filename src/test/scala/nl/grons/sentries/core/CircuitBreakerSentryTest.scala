/*
 * Sentries
 * Copyright (c) 2012 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the BSD 2-Clause License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

package nl.grons.sentries.core

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import nl.grons.sentries.support.NotAvailableException

/**
 * Tests {@link CircuitBreakerSentry}.
 */
class CircuitBreakerSentryTest extends Specification {

  "A CircuitBreaker sentry" should {
    "pass return value in flow state" in new SentryContext {
      for (i <- 1 to 20) {
        sentry(fastCode) must_== "fast"
      }
    }

    "rethrow exceptions in flow state" in new SentryContext {
      for (i <- 1 to 10) {
        sentry.reset() // Needed to stay in flow state.
        sentry(throwAnIllegalArgumentException) must throwA[IllegalArgumentException]
      }
    }

    "be unavailable in broken state" in new SentryContext {
      sentry.trip()
      sentry(fastCode) must throwA[CircuitBreakerBrokenException]
      sentry.trip()
      sentry(throwAnIllegalArgumentException) must throwA[CircuitBreakerBrokenException]
      sentry.trip()
      sentry(throwANotAvailableException) must throwA[CircuitBreakerBrokenException]
      sentry.trip()
      sentry(notInvokedCode) must throwA[CircuitBreakerBrokenException]
    }

    "keep flowing (no exceptions are thrown)" in new SentryContext {
      for (i <- 1 to 20) {
        sentry(fastCode) must not(throwA[CircuitBreakerBrokenException])
      }
    }

    "keep flowing (some exceptions are thrown)" in new SentryContext {
      for (i <- 1 to 10) {
        sentry(fastCode) must not(throwA[CircuitBreakerBrokenException])
        sentry(throwAnIllegalArgumentException) must not(throwA[CircuitBreakerBrokenException])
      }
    }

    "break after failLimit is reached" in new SentryContext {
      sentry(throwAnIllegalArgumentException) must throwA[IllegalArgumentException]
      sentry(throwAnIllegalArgumentException) must throwA[IllegalArgumentException]
      sentry(throwAnIllegalArgumentException) must throwA[IllegalArgumentException]
      sentry(notInvokedCode) must throwA[CircuitBreakerBrokenException]
      sentry(notInvokedCode) must throwA[CircuitBreakerBrokenException]
    }

    "recover to flow state upon success after retry timeout" in new SentryContext {
      sentry.trip()
      Thread.sleep(51L)
      sentry(fastCode) must_== "fast"
      sentry(fastCode) must_== "fast"
    }

    "stay in broken state upon failure after retry timeout" in new SentryContext {
      sentry.trip()
      Thread.sleep(51L)
      // One attempt to call the code:
      sentry(throwAnIllegalArgumentException) must throwA[IllegalArgumentException]
      sentry(notInvokedCode) must throwA[CircuitBreakerBrokenException]
      sentry(notInvokedCode) must throwA[CircuitBreakerBrokenException]
    }

    "break after trip()" in new SentryContext {
      sentry.trip()
      sentry(notInvokedCode) must throwA[CircuitBreakerBrokenException]
    }

    "stay broken after trip()" in new SentryContext {
      sentry(fastCode) must not(throwA[CircuitBreakerBrokenException])
      sentry.trip()
      sentry(notInvokedCode) must throwA[CircuitBreakerBrokenException]
      sentry.trip()
      sentry(notInvokedCode) must throwA[CircuitBreakerBrokenException]
    }

    "recover to flow state after reset()" in new SentryContext {
      sentry.trip()
      sentry.reset()
      sentry(fastCode) must_== "fast"
    }

    "stay in flow state after reset()" in new SentryContext {
      sentry(fastCode) must_== "fast"
      sentry.reset()
      sentry(fastCode) must_== "fast"
    }

    "not break due to NotAvailableException" in new SentryContext {
      for (i <- 1 to 10) {
        sentry(throwANotAvailableException) must not(throwA[CircuitBreakerBrokenException])
      }
    }

    "be multi-thread safe" in {
      todo
    }

    "report status as health" in {
      todo
    }

    "report status as Metrics gauge" in {
      todo
    }
  }

  trait SentryContext extends Scope {
    val sentry = new CircuitBreakerSentry("testSentry", 3, 50L, classOf[CircuitBreakerSentryTest])

    def fastCode = "fast"

    def throwAnIllegalArgumentException: String = {
      throw new IllegalArgumentException("fail")
    }

    def notInvokedCode: String = {
      throw new AssertionError("Should not have been executed")
    }

    def throwANotAvailableException: String = {
      throw new NotAvailableException("abc", "message", null)
    }
  }
}
