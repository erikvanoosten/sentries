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

import org.specs2.mutable.Specification
import nl.grons.sentries.support.NotAvailableException
import org.specs2.specification.Scope
import java.util.concurrent.{ExecutionException, Future, Callable, Executors}
import scala.collection.JavaConverters._

/**
 * Tests [[nl.grons.sentries.core.ConcurrencyLimitSentry]].
 */
class ConcurrencyLimitSentryTest extends Specification {

  "The concurrency limit sentry" should {
    "return value" in new SentryContext {
      sentry("value") must_== "value"
    }

    "rethrow exception" in new SentryContext {
      sentry(throwAnIllegalArgumentException) must throwA[IllegalArgumentException]
    }

    "throw NotAvailableException for too many invocations" in new SentryContext {
      val executor = Executors.newFixedThreadPool(10)
      val options = try {
        val task = new Callable[String] {
          def call() = sentry(slowCode)
        }
        val tasks = Vector.fill(10)(task).asJava

        // Start all 10 tasks simultaneously:
        val futures = executor.invokeAll(tasks).asScala
        futuresToOptions(futures)

      } finally {
        executor.shutdown()
      }

      options.filter(_ == Some("slow")).size must_== 4
      options.filter(_ == None).size must_== 6
    }
  }

  private trait SentryContext extends Scope {
    val sentry = new ConcurrencyLimitSentry(classOf[ConcurrencyLimitSentryTest], "testSentry", 4)

    def slowCode = {
      Thread.sleep(300L)
      "slow"
    }

    def throwAnIllegalArgumentException: String = {
      throw new IllegalArgumentException("fail")
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
