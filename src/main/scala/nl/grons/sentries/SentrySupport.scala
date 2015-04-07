/*
 * Sentries
 * Copyright (c) 2012-2015 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the BSD 2-Clause License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

package nl.grons.sentries

import nl.grons.sentries.support.{InitialSentryBuilder, SentriesRegistry, SentryBuilder}

/**
 * The mixin trait for classes that want to construct sentries.
 *
 * Main usage pattern is as follows:
 * {{{
 *   class YourClass extends nl.grons.sentries.SentrySupport {
 *     private[this] sentry = sentry("resourceName") withXXX() withYYY()
 *   }
 * }}}
 *
 * For the available `withXXX` methods use auto completion or see [[nl.grons.sentries.support.SentryBuilder]].
 */
trait SentrySupport {

  /**
   * Returns a new sentry builder with the current class as owner.
   */
  def sentry(resourceName: String): SentryBuilder =
    new InitialSentryBuilder(getClass, resourceName, sentryRegistry)

  /**
   * Returns the default SentriesRegistry. Override to use another.
   */
  def sentryRegistry = SentrySupport.defaultRegistry
}


object SentrySupport {
  val defaultRegistry = new SentriesRegistry
}
