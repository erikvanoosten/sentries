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

import java.util.concurrent.Semaphore
import nl.grons.sentries.support.{NotAvailableException, ChainableSentry}

/**
 * A sentry that limits the number of concurrent invocations.
 * A new instance can be obtained through the {@link Sentries} mixin.
 */
class ConcurrencyLimitSentry(val resourceName: String, concurrencyLimit: Int) extends ChainableSentry {

  /**
   * @return a simple describing identifier that is unique per sentry chain, e.g. "rateLimit"
   */
  val sentryType = "concurrencyLimit"

  // Note: as we only use tryAcquire, no fairness is necessary.
  private[this] val semaphore = new Semaphore(concurrencyLimit, false)

  /**
   * Run the given code block in the context of this sentry, and return its value.
   *
   * When there are too many concurrent invocations, a {@link NotAvailableException} is thrown.
   */
  def apply[T](r: => T) = {
    if (!semaphore.tryAcquire()) throw new ConcurrencyLimitExceededException(
      resourceName, "Exceeded concurrency limit of " + concurrencyLimit + " for " + resourceName)

    try r finally semaphore.release()
  }

  def reset() {}
}

/**
 * Concurrency limit exceeded, invocations are failing immediately.
 */
class ConcurrencyLimitExceededException(resourceName: String, message: String, cause: Throwable = null) extends NotAvailableException(resourceName, message, cause)
