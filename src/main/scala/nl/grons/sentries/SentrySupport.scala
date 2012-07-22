/*
 * Sentries
 * Copyright (c) 2012 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the BSD 2-Clause License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

package nl.grons.sentries

import nl.grons.sentries.support.{InitialSentryBuilder, SentriesRegistry, SentryBuilder}

/**
 * The mixin trait for classes that failLimit and use resource availability sentries.
 */
trait SentrySupport {

  /**
   * Returns a new sentry builder for the current class.
   */
  def sentry(resourceName: String): SentryBuilder =
    new InitialSentryBuilder(getClass, resourceName, sentryRegistry)

  /**
   * Returns the SentriesRegistry for the class.
   */
  def sentryRegistry = Sentries.defaultRegistry
}


object Sentries {
  val defaultRegistry = new SentriesRegistry
}
