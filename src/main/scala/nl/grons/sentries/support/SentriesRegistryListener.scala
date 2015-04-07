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

import java.util.EventListener
import com.yammer.metrics.core.MetricName

/**
 * Listeners for events from the registry.  Listeners must be thread-safe.
 */
trait SentriesRegistryListener extends EventListener {

  /**
   * Called when a sentry has been added to the [[nl.grons.sentries.support.SentriesRegistry]].
   *
   * @param name   the name of the sentry
   * @param sentry the sentry
   */
  def onSentryAdded(name: MetricName, sentry: NamedSentry)

  /**
   * Called when a sentry has been removed from the [[nl.grons.sentries.support.SentriesRegistry]].
   *
   * @param name the name of the sentry
   */
  def onSentryRemoved(name: MetricName)

}
