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

import akka.dispatch.{ExecutionContext, Await, Future}
import akka.util.duration._
import java.util.concurrent.TimeoutException
import nl.grons.sentries.support.{SentriesRegistry, NotAvailableException, ChainableSentry}

/**
 * A sentry that limits the duration of an invocation.
 * A new instance can be obtained through the {@link Sentries} mixin.
 *
 * WARNING: do NOT use this sentry when you invoke it from 1) a [[akka.dispatch.Future]] or 2) an [[akka.actor.Actor]].
 * For such circumstances you are MUCH better of with a timeout on the enclosing future or a timeout message
 * within the actor.
 * Reason: this sentry blocks the current thread while waiting on a future that executes the task. Blocking the
 * current thread is an anti-pattern for futures and actors.
 */
class DurationLimitSentry(val resourceName: String, durationLimitMillis: Long) extends ChainableSentry {
  val sentryType = "durationLimit"

  private[this] val duration = durationLimitMillis.milliseconds

  /**
   * Run the given code block in the context of this sentry, and return its value.
   *
   * When an invocations takes too long, a {@link NotAvailableException} is thrown.
   */
  def apply[T](r: => T) = {
    try {
      Await.result(Future(r)(DurationLimitSentry.ec), duration)
    } catch {
      case e: TimeoutException =>
        throw new DurationLimitExceededException(
          resourceName, "Timeout of %s exceeded for resource %s".format(duration, resourceName), e)
    }
  }

  def reset() {}
}

object DurationLimitSentry {
  implicit private val ec = ExecutionContext.fromExecutorService(SentriesRegistry.executor)
}

/**
 * Duration limit of an invocation was exceeded.
 */
class DurationLimitExceededException(resourceName: String, message: String, cause: Throwable = null)
  extends NotAvailableException(resourceName, message, cause)
