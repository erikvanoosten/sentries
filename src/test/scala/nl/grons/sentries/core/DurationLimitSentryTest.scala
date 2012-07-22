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

import org.specs2.specification.Scope
import nl.grons.sentries.support.NotAvailableException

/**
 * Tests {@link DurationLimitSentry}.
 */
class DurationLimitSentryTest extends org.specs2.mutable.Specification {

  "The sentry" should {
    "return value for timely code" in new SentryContext {
      sentry(quickCode) must_== "fast"
    }

    "throw NotAvailableException for slow code" in new SentryContext {
      sentry(slowCode) must throwA[NotAvailableException]
    }

    "return value for timely code" in new SentryContext {
      sentry(failingCode) must throwA[IllegalArgumentException]
    }
  }

  trait SentryContext extends Scope {
    val sentry = new DurationLimitSentry("testSentry", 10L)

    def quickCode = "fast"

    def slowCode = {
      Thread.sleep(20L)
      "slow"
    }

    def failingCode: String = {
      throw new IllegalArgumentException("fail")
    }
  }

}

