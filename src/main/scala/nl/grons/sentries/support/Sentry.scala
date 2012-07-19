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

package nl.grons.sentries.support

/**
 * A resource availability sentry.
 */
trait Sentry {

  /**
   * Run the given code block in the context of this sentry, and return its value.
   */
  def apply[T](r: => T): T

  /**
   * Go back to the initial state.
   */
  def reset()

  /**
   * Composes two instances of Sentry in a new Sentry, with this sentries context applied first.
   * @return a new sentry t such that t(x) == s(this(x))
   */
  def andThen(s: Sentry): Sentry = s.compose(this)

  /**
   * Composes two instances of Sentry in a new Sentry, with this sentries context applied last.
   * @return a new sentry t such that t(x) == this(s(x))
   */
  def compose(s: Sentry): Sentry = {
    val self = this
    new Sentry {
      def apply[T](r: => T) = self(s(r))
      def reset() {
        self.reset()
        s.reset()
      }
    }
  }
}

/**
 * A named resource availability sentry.
 */
trait NamedSentry extends Sentry {
  /**
   * @return a string describing the resource that is protected, e.g. "mysql:server-b.com:3336"
   *         It is used in exceptions and visible through JMX.
   */
  def resourceName: String
}

trait ChainableSentry extends NamedSentry {
  /**
   * @return a simple describing identifier that is unique per sentry chain, e.g. "rateLimit"
   */
  def sentryType: String
}
