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

import com.yammer.metrics.core.{Stoppable, MetricName}
import java.util.concurrent.{Executors, CopyOnWriteArrayList}
import scala.collection.concurrent.{Map => CMap}
import scala.collection.concurrent.TrieMap.{empty => emptyCMap}
import scala.collection.JavaConverters._

/**
 * A registry of sentry instances.
 */
class SentriesRegistry() {

  private[this] val listeners = new CopyOnWriteArrayList[SentriesRegistryListener]().asScala
  private[this] val sentries: CMap[MetricName, NamedSentry] = newSentriesMap()

  /**
   * Adds a [[nl.grons.sentries.support.SentriesRegistryListener]] to a collection of listeners that will
   * be notified on sentry creation.  Listeners will be notified in the order in which they are added.
   * <p/>
   * <b>N.B.:</b> The listener will be notified of all existing sentries when it first registers.
   *
   * @param listener the listener that will be notified
   */
  def addListener(listener: SentriesRegistryListener) {
    listeners += listener
    sentries.foreach {
      case (name, sentry) => listener.onSentryAdded(name, sentry)
    }
  }

  /**
   * Removes a [[nl.grons.sentries.support.SentriesRegistryListener]] from this registry's collection of listeners.
   *
   * @param listener the listener that will be removed
   */
  def removeListener(listener: SentriesRegistryListener) {
    listeners -= listener
  }

  /**
   * Gets any existing sentry with the given name or, if none exists, adds the given sentry.
   *
   * @param sentry the sentry to add
   * @param sentryOwner the class that owns the sentry
   * @param name name of the sentry
   * @param sentryType sentryType type of sentry
   * @tparam S type of the sentry
   * @return either the existing sentry or `sentry`
   */
  def getOrAdd[S <: NamedSentry](sentry: S, sentryOwner: Class[_], name: String, sentryType: String): S =
    getOrAdd(createName(sentryOwner, name, sentryType), sentry)

  /**
   * Removes the sentry for the given class with the given name (and sentryType).
   *
   * @param sentryOwner the class that owns the sentry
   * @param name  the name of the sentry
   * @param sentryType the sentryType of the sentry
   */
  def removeSentry(sentryOwner: Class[_], name: String, sentryType: String) {
    removeSentry(createName(sentryOwner, name, sentryType))
  }

  /**
   * Removes the sentry with the given name.
   *
   * @param name the name of the sentry
   */
  def removeSentry(name: MetricName) {
    sentries.remove(name).map { sentry =>
      if (sentry.isInstanceOf[Stoppable]) sentry.asInstanceOf[Stoppable].stop()
      notifySentriesRemoved(name)
    }
  }

  /**
   * Remove all sentries from the registry.
   *
   * See README.md section 'Sentries in tests' for alternatives during testing.
   */
  @deprecated(message = "will be removed in sentries 0.6", since = "0.5")
  def clear() {
    val sentryNames = Set() ++ sentries.keySet
    sentryNames.map(sentryName => removeSentry(sentryName))
  }

  /**
   * Reset all known sentries to their initial state by calling [Sentry#reset] on each sentry.
   *
   * See README.md section 'Sentries in tests' for alternatives during testing.
   */
  def resetAllSentries() {
    sentries.foreach {
      case (_, sentry) => sentry.reset()
    }
  }

  /**
   * Override to customize how [[com.yammer.metrics.core.MetricName]]s are created.
   *
   * @param sentryOwner the class which owns the sentry
   * @param name  the name of the sentry
   * @param sentryType the sentry's sentryType
   * @return the sentry's full name
   */
  protected def createName(sentryOwner: Class[_], name: String, sentryType: String): MetricName =
    new MetricName(sentryOwner, name + "." + sentryType)

  /**
   * Returns a new concurrent map implementation. Subclass this to do weird things with
   * your own [[nl.grons.sentries.support.SentriesRegistry]] implementation.
   *
   * @return a new [[scala.collection.concurrent.Map]]
   */
  protected def newSentriesMap(): CMap[MetricName, NamedSentry] = emptyCMap

  /**
   * Gets any existing sentry with the given name or, if none exists, adds the given sentry.
   *
   * @param name   the sentry's name
   * @param sentry the new sentry
   * @tparam S     the type of the sentry
   * @return either the existing sentry or `sentry`
   */
  private def getOrAdd[S <: NamedSentry](name: MetricName, sentry: S): S = {
    sentries.putIfAbsent(name, sentry) match {
      case Some(existing) =>
        if (sentry.isInstanceOf[Stoppable]) sentry.asInstanceOf[Stoppable].stop()
        existing.asInstanceOf[S]
      case None =>
        notifySentriesAdded(name, sentry)
        sentry
    }
  }

  private def notifySentriesRemoved(name: MetricName) {
    listeners.foreach(_.onSentryRemoved(name))
  }

  private def notifySentriesAdded(name: MetricName, sentry: NamedSentry) {
    listeners.foreach(_.onSentryAdded(name, sentry))
  }

}

object SentriesRegistry {
  val executor = Executors.newCachedThreadPool()
}
