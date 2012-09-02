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
import nl.grons.sentries.support.{Sentry, NotAvailableException}

/**
 * Tests [[nl.grons.sentries.core.CircuitBreakerSentry]].
 */
class CircuitBreakerSentryTest extends Specification {

  "A CircuitBreaker sentry" should {
    "pass return value in flow state" in new SentryContext {
      for (i <- 1 to 20) {
        sentry("1")(fastCode) must_== "fast"
      }
    }

    "rethrow exceptions in flow state" in new SentryContext {
      val s = sentry("2")
      for (i <- 1 to 10) {
        s.reset() // Needed to stay in flow state.
        s(throwAnIllegalArgumentException) must throwA[IllegalArgumentException]
      }
    }

    "be unavailable in broken state" in new SentryContext {
      val s = sentry("3")
      s.trip()
      s(fastCode) must throwA[CircuitBreakerBrokenException]
      s.trip()
      s(throwAnIllegalArgumentException) must throwA[CircuitBreakerBrokenException]
      s.trip()
      s(throwANotAvailableException) must throwA[CircuitBreakerBrokenException]
      s.trip()
      s(notInvokedCode) must throwA[CircuitBreakerBrokenException]
    }

    "keep flowing (no exceptions are thrown)" in new SentryContext {
      val s = sentry("4")
      for (i <- 1 to 20) {
        s(fastCode) must not(throwA[CircuitBreakerBrokenException])
      }
    }

    "keep flowing (control exceptions are thrown)" in new SentryContext {
      val s = sentry("5")
      for (i <- 1 to 20) {
        succeedingByBreakingOutOfClosure(s) must_== "yes"
      }
    }

    "keep flowing (some exceptions are thrown)" in new SentryContext {
      val s = sentry("6")
      for (i <- 1 to 10) {
        s(fastCode) must not(throwA[CircuitBreakerBrokenException])
        s(throwAnIllegalArgumentException) must not(throwA[CircuitBreakerBrokenException])
      }
    }

    "break after failLimit is reached" in new SentryContext {
      val s = sentry("7")
      s(throwAnIllegalArgumentException) must throwA[IllegalArgumentException]
      s(throwAnIllegalArgumentException) must throwA[IllegalArgumentException]
      s(throwAnIllegalArgumentException) must throwA[IllegalArgumentException]
      s(notInvokedCode) must throwA[CircuitBreakerBrokenException]
      s(notInvokedCode) must throwA[CircuitBreakerBrokenException]
    }

    "recover to flow state upon success after retry timeout" in new SentryContext {
      val s = sentry("8")
      s.trip()
      Thread.sleep(101L)
      s(fastCode) must_== "fast"
      s(fastCode) must_== "fast"
    }

    "stay in broken state upon failure after retry timeout" in new SentryContext {
      val s = sentry("9")
      s.trip()
      Thread.sleep(101L)
      // One attempt to call the code:
      s(throwAnIllegalArgumentException) must throwA[IllegalArgumentException]
      s(notInvokedCode) must throwA[CircuitBreakerBrokenException]
      s(notInvokedCode) must throwA[CircuitBreakerBrokenException]
    }

    "break after trip()" in new SentryContext {
      val s = sentry("10")
      s.trip()
      s(notInvokedCode) must throwA[CircuitBreakerBrokenException]
    }

    "stay broken after trip()" in new SentryContext {
      val s = sentry("11")
      s(fastCode) must not(throwA[CircuitBreakerBrokenException])
      s.trip()
      s(notInvokedCode) must throwA[CircuitBreakerBrokenException]
      s.trip()
      s(notInvokedCode) must throwA[CircuitBreakerBrokenException]
    }

    "recover to flow state after reset()" in new SentryContext {
      val s = sentry("12")
      s.trip()
      s.reset()
      s(fastCode) must_== "fast"
    }

    "stay in flow state after reset()" in new SentryContext {
      val s = sentry("13")
      s(fastCode) must_== "fast"
      s.reset()
      s(fastCode) must_== "fast"
    }

    "not break due to NotAvailableException" in new SentryContext {
      val s = sentry("14")
      for (i <- 1 to 10) {
        s(throwANotAvailableException) must not(throwA[CircuitBreakerBrokenException])
      }
    }

    "be multi-thread safe" in { todo }
    "report status as health" in { todo }
    "report status as Metrics gauge" in { todo }
  }

  trait SentryContext extends Scope {
    def sentry(resourceName: String) =
      new CircuitBreakerSentry(resourceName, 3, 100L, classOf[CircuitBreakerSentryTest])

    def fastCode = "fast"

    def succeedingByBreakingOutOfClosure(s: Sentry): String = s {
      if (1 + 1 == 2) return "yes"
      if (2 + 2 == 4) return "no"
      "no"
    }

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
