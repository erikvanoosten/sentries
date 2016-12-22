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

import java.util.concurrent.{Executors, CopyOnWriteArrayList}
import nl.grons.metrics.scala.MetricName

import scala.collection.concurrent.{Map => CMap}
import scala.collection.concurrent.TrieMap.{empty => emptyCMap}
import scala.collection.JavaConverters._

/**
 * A registry of sentry instances.
 */
class SentriesRegistry() {

  private[this] val listeners = new CopyOnWriteArrayList[SentriesRegistryListener]().asScala
  private[this] val sentries: CMap[String, NamedSentry] = newSentriesMap()

  /**
   * Adds a [[nl.grons.sentries.support.SentriesRegistryListener]] to a collection of listeners that will
   * be notified on sentry creation.  Listeners will be notified in the order in which they are added.
   * <p/>
   * <b>N.B.:</b> The listener will be notified of all existing sentries when it first registers.
   *
   * @param listener the listener that will be notified
   */
  def addListener(listener: SentriesRegistryListener): Unit = {
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
  def removeListener(listener: SentriesRegistryListener): Unit = {
    listeners -= listener
  }

  /**
   * Adds the given sentry, or gets any existing sentry with the same name.
   *
   * @param sentry the sentry to add
   * @tparam S type of the sentry
   * @return either the existing sentry or `sentry`
   */
  def getOrAdd[S <: NamedSentry](sentry: S): S = {
    val name = sentryNameFor(sentry)
    sentries.putIfAbsent(name, sentry) match {
      case Some(existing) =>
        existing.asInstanceOf[S]
      case None =>
        notifySentriesAdded(name, sentry)
        sentry
    }
  }

  /**
   * Removes a sentry.
   *
   * @param sentry the sentry to remove
   */
  def removeSentry(sentry: NamedSentry): Unit = {
    removeSentry(sentryNameFor(sentry))
  }

  private def removeSentry(sentryName: String): Unit = {
    sentries.remove(sentryName).map { sentry =>
      notifySentriesRemoved(sentryName)
    }
  }

  /**
   * Reset all known sentries to their initial state by calling [Sentry#reset] on each sentry.
   *
   * See README.md section 'Sentries in tests' for alternatives during testing.
   */
  def resetAllSentries(): Unit = {
    sentries.foreach {
      case (_, sentry) => sentry.reset()
    }
  }

  protected def sentryNameFor(sentry: NamedSentry): String =
    MetricName(sentry.owner, sentry.resourceName, sentry.sentryType).name

  /**
   * Returns a new concurrent map implementation. Subclass this to do weird things with
   * your own [[nl.grons.sentries.support.SentriesRegistry]] implementation.
   *
   * @return a new [[scala.collection.concurrent.Map]]
   */
  protected def newSentriesMap(): CMap[String, NamedSentry] = emptyCMap
  
  private def notifySentriesRemoved(name: String): Unit = {
    listeners.foreach(_.onSentryRemoved(name))
  }

  private def notifySentriesAdded(name: String, sentry: NamedSentry): Unit = {
    listeners.foreach(_.onSentryAdded(name, sentry))
  }

}

object SentriesRegistry {
  /** Executor used by [[nl.grons.sentries.core.DurationLimitSentry]]. */
  lazy val executor = Executors.newCachedThreadPool()
}
