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
import nl.grons.sentries.support.NotAvailableException
import org.specs2.specification.Scope
import java.util.concurrent.{ExecutionException, Future, Callable, Executors}
import scala.collection.JavaConverters._

/**
 * Tests {@link RateLimitSentry}.
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
      Thread.sleep(21L)
      sentry(fastCode) must_== "fast"
    }

    "be multi-thread safe" in new SentryContext {
      val executor = Executors.newFixedThreadPool(10)
      try {
        val task = new Callable[String] {
          def call() = sentry(fastCode)
        }
        val tasks = Vector.fill(10)(task).asJava

        // Start all 10 tasks simultaneously:
        val futures = executor.invokeAll(tasks).asScala
        futuresToOptions(futures).filter(_ == Some("fast")).size must_== 3

        Thread.sleep(21L)

        // Once more:
        val futures2 = executor.invokeAll(tasks).asScala
        futuresToOptions(futures2).filter(_ == Some("fast")).size must_== 3

      } finally {
        executor.shutdown()
      }
    }

    "this line keeps intellij happy" in { todo }
  }

  trait SentryContext extends Scope {
    val sentry = new RateLimitSentry("testSentry", 3, 20L, classOf[ConcurrencyLimitSentryTest])

    def fastCode = "fast"

    def slowCode = {
      Thread.sleep(5L)
      "slow"
    }

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
        case e => throw e
      }
    }
  }

}
