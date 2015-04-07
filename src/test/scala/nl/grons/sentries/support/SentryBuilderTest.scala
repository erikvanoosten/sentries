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

import org.specs2.mutable.Specification
import nl.grons.sentries.SentrySupport
import org.specs2.specification.Scope

/**
 * Tests [[nl.grons.sentries.support.SentryBuilder]].
 */
class SentryBuilderTest extends Specification {

  "A sentry builder" should {
    "return value" in new BuilderContext {
      sentry("test").withSentry(testSentry(1))("value") must_== "value"
    }

    "build a sentry that executes tasks in correct order" in new BuilderContext {
      val s = sentry("test").withSentry(testSentry(1)).withSentry(testSentry(2))
      s(null)
      executedTaskIds must containTheSameElementsAs(Seq(1, 2))
    }

    "allow chaining sentries on a base sentry builder and execute tasks in correct order" in new BuilderContext {
      val base = sentry("test").withSentry(testSentry(1))
      val s1 = base.withSentry(testSentry(2)).withSentry(testSentry(3))
      val s2 = base.withSentry(testSentry(4)).withSentry(testSentry(5))
      s1(null)
      executedTaskIds must containTheSameElementsAs(Seq(1, 2, 3))

      // reset:
      executedTaskIds = Seq[Int]()
      s2(null)
      executedTaskIds must containTheSameElementsAs(Seq(1, 4, 5))
    }

    "allow embedding other chains and execute tasks in correct order" in new BuilderContext {
      val chain = sentry("chain").withSentry(testSentry(10)).withSentry(testSentry(11))
      val s = sentry("test").withSentry(testSentry(1)).withSentry(chain).withSentry(testSentry(2))
      s(null)
      executedTaskIds must containTheSameElementsAs(Seq(1, 10, 11, 2))
    }

    "add fail limit sentry" in { todo }
    "add concurrency limit sentry" in { todo }
    "add rate limit sentry" in { todo }
    "add duration limit sentry" in { todo }
    "add metrics sentry" in { todo }
    "add simple metrics sentry" in { todo }
    "be multi-thread safe" in { todo }
  }

  trait BuilderContext extends Scope with SentrySupport {
    var executedTaskIds = Seq[Int]()

    def testSentry(taskId: Int) = new ChainableSentry {
      def sentryType = "task-" + taskId
      def resourceName = "test"
      def apply[T](r: => T) = {
        executedTaskIds :+= taskId
        r
      }
      def reset() {}
    }
  }

}
