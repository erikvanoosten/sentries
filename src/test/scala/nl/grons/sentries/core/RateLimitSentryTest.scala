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

import java.util.concurrent._
import nl.grons.sentries.support.NotAvailableException
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.duration.Duration
import scala.collection.JavaConverters._

/**
 * Tests [[nl.grons.sentries.core.RateLimitSentry]].
 */
class RateLimitSentryTest extends Specification {
  "The rate limit sentry" should {
    "return value" in new SentryContext {
      sentry("value") must_== "value"
    }

    "rethrow exception" in new SentryContext {
      sentry(throwAnIllegalArgumentException) must throwA[IllegalArgumentException]
    }

    "throw NotAvailableException for too many invocations" in new SentryContext {
      sentry(fastCode) must_== "fast"
      sentry(fastCode) must_== "fast"
      sentry(fastCode) must_== "fast"
      sentry(notInvokedCode) must throwA[NotAvailableException]
    }

    "allow invocations in next period" in new SentryContext {
      sentry(fastCode) must_== "fast"
      sentry(fastCode) must_== "fast"
      sentry(fastCode) must_== "fast"
      sentry(notInvokedCode) must throwA[NotAvailableException]
      Thread.sleep(delay + 5L)
      sentry(fastCode) must_== "fast"
    }

    "while limited allow invocations after reset()" in new SentryContext {
      todo
    }

    "keep allowing invocations after reset()" in new SentryContext {
      todo
    }

    "be multi-thread safe" in new SentryContext {
      val executor = Executors.newFixedThreadPool(10)
      try {
        // The barrier makes sure all threads start roughly at the same time. This is needed because
        // create threads has a wide latency variance.
        val barrier = new CyclicBarrier(10)
        val task = new Callable[String] {
          def call() = {
            // The long timeout makes sure that even in the event of failures, the test will complete.
            barrier.await(3 * delay, TimeUnit.MILLISECONDS)
            sentry(fastCode)
          }
        }
        val tasks = Vector.fill(10)(task).asJava

        // Start all 10 tasks simultaneously:
        val futures = executor.invokeAll(tasks).asScala
        futuresToOptions(futures).count(_ == Some("fast")) must_== 3

        Thread.sleep(delay + 5L)

        // Once more:
        val futures2 = executor.invokeAll(tasks).asScala
        futuresToOptions(futures2).count(_ == Some("fast")) must_== 3

      } finally {
        executor.shutdown()
      }
    }
  }

  private trait SentryContext extends Scope {
    val delay: Long = 300L
    val sentry = new RateLimitSentry(classOf[RateLimitSentryTest], "testSentry", 3, Duration(delay, TimeUnit.MILLISECONDS))

    def fastCode = "fast"

    def throwAnIllegalArgumentException: String = {
      throw new IllegalArgumentException("fail")
    }

    def notInvokedCode: String = {
      throw new AssertionError("Should not have been executed")
    }

    // Convert normal Future results to a Some, and NotAvailableExceptions
    // (wrapped in ExecutionException) to a None.
    def futuresToOptions[A](futures: Seq[Future[A]]): Seq[Option[A]] = futures.map { future =>
      try Some(future.get())
      catch {
        case e: ExecutionException if e.getCause.isInstanceOf[NotAvailableException] => None
      }
    }
  }

}
