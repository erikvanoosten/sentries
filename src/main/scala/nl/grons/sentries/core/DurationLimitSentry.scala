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

import java.util.concurrent.TimeoutException
import nl.grons.sentries.support.{SentriesRegistry, NotAvailableException, ChainableSentry}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Await, ExecutionContext}

/**
 * A sentry that limits the duration of an invocation.
 * A new instance can be obtained through the [[nl.grons.sentries.SentrySupport]] mixin.
 *
 * The goal of a duration limiter is to support callers that are only interested
 * in the results for a limited time.
 *
 * WARNING: do NOT use this sentry when you invoke it from a `Future` or an `Actor`.
 * For such circumstances you are MUCH better of with a timeout on the enclosing future or a timeout message
 * within the actor.
 * Reason: this sentry blocks the current thread while waiting on a future that executes the task. Blocking the
 * current thread is an anti-pattern for futures and actors.
 *
 * Note that when the wait period has passed, the task still completes in another thread. Make
 * sure there are enough threads in the executor. By default a `Executors.newCachedThreadPool()`
 * is used which creates as much threads as needed. The executor can be changed by overriding
 * [[.executionContext]].
 */
class DurationLimitSentry(val resourceName: String, durationLimit: FiniteDuration) extends ChainableSentry {
  val sentryType = "durationLimit"

  lazy val executionContext = DurationLimitSentry.ec

  /**
   * Run the given code block in the context of this sentry, and return its value.
   *
   * When an invocations takes too long, a [[nl.grons.sentries.support.NotAvailableException]] is thrown.
   */
  def apply[T](r: => T) = {
    try {
      Await.result(Future(r)(executionContext), durationLimit)
    } catch {
      case e: TimeoutException =>
        throw new DurationLimitExceededException(
          resourceName, "Timeout of %s exceeded for resource %s".format(durationLimit, resourceName), e)
    }
  }

  def reset() {}
}

object DurationLimitSentry {
  private lazy val ec: ExecutionContext = ExecutionContext.fromExecutorService(SentriesRegistry.executor)
}

/**
 * Duration limit of an invocation was exceeded.
 */
class DurationLimitExceededException(resourceName: String, message: String, cause: Throwable = null)
  extends NotAvailableException(resourceName, message, cause)
