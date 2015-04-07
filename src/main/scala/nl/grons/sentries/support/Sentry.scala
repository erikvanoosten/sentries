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

/**
 * A resource availability sentry, 'Sentry' for short.
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
   * @return a string describing the resource that is protected, e.g. "mysql:server-b.com:3336".
   *         It is used in exceptions and visible through JMX.
   */
  def resourceName: String
}

trait ChainableSentry extends NamedSentry {
  /**
   * @return a simple describing identifier that is unique per sentry chain, e.g. "rateLimit".
   *         `ResourceName` plus `sentryType` uniquely name each sentry. The sentry registry
   *         enforces this. The `sentryType` is also used in JMX to uniquely name bean properties
   *         for a resource.
   *         `null` for sentry wrappers, that must not be registered.
   */
  def sentryType: String
}
