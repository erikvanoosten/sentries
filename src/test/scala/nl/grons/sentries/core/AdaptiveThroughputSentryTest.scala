/*
 * Sentries
 * Copyright (c) 2012-2013 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the BSD 2-Clause License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

package nl.grons.sentries.core

import java.util.concurrent.TimeUnit
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import nl.grons.sentries.support.{Sentry, NotAvailableException}
import nl.grons.sentries.cross.Concurrent._

/**
 * Tests [[nl.grons.sentries.core.AdaptiveThroughputSentry]].
 */
class AdaptiveThroughputSentryTest extends Specification {

  "A adaptive throughput sentry" should {
    "pass return value" in new SentryContext {
      for (i <- 1 to 20) {
        sentry(fastCode) must_== "fast"
      }
    }

    "rethrow exceptions" in new SentryContext {
      for (i <- 1 to 10) {
        sentry(throwAnIllegalArgumentException) must throwA[IllegalArgumentException]
      }
    }

    "be unavailable when throughput is 0" in new SentryContext {
      sentry.trip()
      sentry(fastCode) must throwA[ReducedThroughputException]
      sentry(throwAnIllegalArgumentException) must throwA[ReducedThroughputException]
      sentry(throwANotAvailableException) must throwA[ReducedThroughputException]
      sentry(notInvokedCode) must throwA[ReducedThroughputException]
    }

    "always be available for fast resources (no exceptions are thrown)" in new SentryContext {
      for (i <- 1 to 20) {
        sentry(fastCode) must not(throwA[ReducedThroughputException])
      }
    }

    "always be available for fast resources (control exceptions are thrown)" in new SentryContext {
      for (i <- 1 to 20) {
        succeedingByBreakingOutOfClosure(sentry) must_== "yes"
      }
    }

    "always be available for fast resources (a few exceptions are thrown)" in new SentryContext {
      // 1 in 10 throw an exception -> fail ratio is below target fail ratio
      trip(1)

      // Throughput must not be affected.
      sentry.currentThroughputRatio must_== 1.0D
    }

    "reduce throughput after success ratio dropped below target" in new SentryContext {
      // 3 in 10 throw an exception -> fail ratio is below target fail ratio
      trip(3)
      // Throughput must be reduced.
      sentry.currentThroughputRatio must_== 0.7D

      // 10 in 10 throw an exception -> fail ratio is still below target fail ratio
      // Note: we must let all invocations fail, as only 30% of all calls are let through, there exists
      // the chance that not enough of our failures are invoked.
      trip(10, 1)
      // Throughput must be reduced further (close to 0, we only 1 succeeding attempt at the end of trip()).
      sentry.currentThroughputRatio must beCloseTo(0.0D, 0.1D)
    }

    "increase throughput after success ratio increased above target" in new SentryContext {
      // 3 in 10 throw an exception -> fail ratio is below target fail ratio
      trip(3)
      // Throughput must be reduced.
      sentry.currentThroughputRatio must_== 0.7D

      // 1 in 10 throw an exception -> fail ratio is above target fail ratio again
      trip(1, 1)
      // Throughput must be increased.
      sentry.currentThroughputRatio must_== 1.2D * 0.7D
    }

    "allow access to resource once every evaluation period even when throughput is 0" in new SentryContext {
      // Set throughput to 0. Next evaluation is in evaluationDelay millis.
      sentry.trip()

      // Run for 2 whole evaluation periods and a bit of the third.
      val start = System.currentTimeMillis()
      val endAt = start + (evaluationDelay * 2.5D).toLong
      var resourceInvokedCount = 0
      var resourceInvocationBlockedCount = 0
      while (System.currentTimeMillis() < endAt) {
        try sentry(throwAnIllegalArgumentException)
        catch {
          case _: IllegalArgumentException => resourceInvokedCount += 1
          case _: ReducedThroughputException => resourceInvocationBlockedCount += 1
          case _ => anError
        }
        Thread.sleep(8L)
      }

      // Current throughput must still be 0, otherwise some other bug was triggered.
      sentry.currentThroughputRatio must_== 0.0D
      // Lots of invocations must have been blocked, otherwise some other bug was triggered.
      resourceInvocationBlockedCount must be_>(50)
      // Two periods were started after sentry.trip(), each must have resulted in 1 allowed resource invocation.
      resourceInvokedCount must_== 2
    }

    "not reduce throughput due to NotAvailableException" in new SentryContext {
      // 3 out of 10 throw a NotAvailableException
      trip(3, 0, throwANotAvailableException)

      // Throughput must not be affected.
      sentry.currentThroughputRatio must_== 1.0D
    }

    "be multi-thread safe" in { todo }
    "report status as health" in { todo }
    "report status as Metrics gauge" in { todo }
  }

  trait SentryContext extends Scope {
    val evaluationDelay: Long = 500L
    val sentry = new AdaptiveThroughputSentry(
      "testSentry",
      0.8,
      classOf[AdaptiveThroughputSentryTest],
      Duration(evaluationDelay, TimeUnit.MILLISECONDS)
    )

    /**
     * Invoke the resource `1000 - skipSuccess` times, and then once more in the next state.
     * Of each 10 invocations, `failureCountPerTen` fail, the rest succeeds.
     */
    def trip(failureCountPerTen: Int, skipSuccess: Int = 0, failure: => String = throwAnIllegalArgumentException) {
      require(failureCountPerTen >= 0 && failureCountPerTen <= 10 && skipSuccess >= 0)
      val start = System.currentTimeMillis()
      var successSkipped = 0
      for (i <- 0 until 1000) {
        if (i % 10 < failureCountPerTen)
          ignoringExceptions(sentry(failure))
        else if (successSkipped < skipSuccess)
          successSkipped += 1
        else
          ignoringExceptions(sentry(fastCode))
      }

      // Wait till throughput will be updated, then trigger next state. If wait time is less then 5, your
      // computer is not fast enough. Please reduce the number of invocations in the loop, or increase
      // the sentry's evaluation delay.
      val waitTime = System.currentTimeMillis() - start + evaluationDelay + 10
      waitTime must be_>(5L)
      Thread.sleep(waitTime)
      ignoringExceptions(sentry(fastCode))
    }

    def ignoringExceptions(r: => Unit) {
      try r catch { case _: Exception => /*empty*/ }
    }

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
