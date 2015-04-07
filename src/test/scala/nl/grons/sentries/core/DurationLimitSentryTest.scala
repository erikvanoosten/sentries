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

import java.util.concurrent.{TimeUnit, Executor}
import nl.grons.sentries.support.{SentriesRegistry, NotAvailableException}
import org.specs2.specification.Scope
import scala.concurrent.duration._

/**
 * Tests [[nl.grons.sentries.core.DurationLimitSentry]].
 */
class DurationLimitSentryTest extends org.specs2.mutable.Specification {

  "The duration limit sentry" should {
    "return value for timely code" in new SentryContext {
      sentry(fastCode) must_== "fast"
    }

    "throw NotAvailableException for slow code" in new SentryContext {
      sentry(slowCode) must throwA[NotAvailableException]
    }

    "return value for timely code" in new SentryContext {
      sentry(failingCode) must throwA[ExpectedException]
    }

    "be multi-thread safe" in {
      todo
    }
  }

  private trait SentryContext extends Scope {
    val sentry = new DurationLimitSentry("testSentry", Duration(200L, TimeUnit.MILLISECONDS)) {
      override lazy val executionContext = nonLoggingExecutionContext(SentriesRegistry.executor)
    }

    def fastCode = "fast"

    def slowCode = {
      Thread.sleep(300L)
      "slow"
    }

    def failingCode: String = {
      throw new ExpectedException
    }
  }

  private def nonLoggingExecutionContext(executor: Executor): scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.fromExecutor(executor, reporter = (_: Throwable) => ())

}

class ExpectedException extends Exception
