/*
 * Sentries
 * Copyright (c) 2012 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the Revised BSD License.
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
