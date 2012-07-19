/*
 * Copyright (c) 2012 Erik van Oosten
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nl.grons.sentries.core

import akka.dispatch.{ExecutionContext, Await, Future}
import akka.util.duration._
import java.util.concurrent.{Executors, TimeoutException}
import nl.grons.sentries.support.{NotAvailableException, ChainableSentry}

/**
 * A sentry that limits the duration of an invocation.
 * A new instance can be obtained through the {@link Sentries} mixin.
 */
class DurationLimitSentry(val resourceName: String, durationLimitMillis: Long) extends ChainableSentry {
  val sentryType = "durationLimit"

  private[this] val duration = durationLimitMillis.milliseconds

  implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  /**
   * Run the given code block in the context of this sentry, and return its value.
   *
   * When an invocations takes too long, a {@link NotAvailableException} is thrown.
   */
  def apply[T](r: => T) = {
    try {
      Await.result(Future(r), duration)
    } catch {
      case e: TimeoutException =>
        throw new DurationLimitExceededException(
          resourceName, "Timeout of %s exceeded for resource %s".format(duration, resourceName), e)
    }
  }

  def reset() {}
}

/**
 * Duration limit of an invocation was exceeded.
 */
class DurationLimitExceededException(resourceName: String, message: String, cause: Throwable = null)
  extends NotAvailableException(resourceName, message, cause)
