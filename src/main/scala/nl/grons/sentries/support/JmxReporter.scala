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

import java.lang.management.ManagementFactory
import javax.management.{MBeanRegistrationException, InstanceNotFoundException, ObjectName, MBeanServer}
import com.codahale.metrics.{DefaultObjectNameFactory, ObjectNameFactory}
import nl.grons.sentries
import nl.grons.sentries.SentrySupport
import org.slf4j.LoggerFactory
import scala.collection.concurrent.{Map => CMap}
import scala.collection.concurrent.TrieMap.{empty => emptyCMap}

/**
 * A reporter which exposes sentries as JMX MBeans.
 */
class JmxReporter(
  sentryRegistry: SentriesRegistry = SentrySupport.defaultRegistry,
  server: MBeanServer = ManagementFactory.getPlatformMBeanServer,
  domain: String = "sentries",
  objectNameFactory: ObjectNameFactory = new DefaultObjectNameFactory()
) extends SentriesRegistryListener {

  private[this] var listening = false
  private[this] val registeredBeans: CMap[String, ObjectName] = newRegisteredBeansMap()
  private[this] val logger = LoggerFactory.getLogger(getClass)

  /**
   * Called when a sentry has been added to the [[nl.grons.sentries.support.SentriesRegistry]].
   *
   * @param name   the name of the sentry
   * @param sentry the sentry
   */
  def onSentryAdded(name: String, sentry: NamedSentry): Unit = {
    registerBean(name, createMBean(sentry), mbeanNameFor(name))
  }

  private def createMBean(sentry: NamedSentry): JmxReporter.SentryMBean = {
    sentry match {
      case s: sentries.core.CircuitBreakerSentry => new JmxReporter.CircuitBreakerSentry(s)
      case s: sentries.core.AdaptiveThroughputSentry => new JmxReporter.AdaptiveThroughputSentry(s)
      case s => new JmxReporter.Sentry(s)
    }
  }

  /**
   * Called when a sentry has been removed from the [[nl.grons.sentries.support.SentriesRegistry]].
   *
   * @param sentryName the name of the sentry
   */
  def onSentryRemoved(sentryName: String): Unit = {
    unregisterBean(mbeanNameFor(sentryName))
  }

  private def mbeanNameFor(sentryName: String): ObjectName = {
    objectNameFactory.createName("sentry", domain, sentryName)
  }

  /**
   * Returns a new concurrent map implementation. Subclass this to do weird things with
   * your own [[nl.grons.sentries.support.JmxReporter]] implementation.
   *
   * @return a new [[scala.collection.concurrent.Map]]
   */
  protected def newRegisteredBeansMap(): CMap[String, ObjectName] = emptyCMap

  def shutdown(): Unit = {
    sentryRegistry.removeListener(this)
    registeredBeans.values.foreach(unregisterBean)
    registeredBeans.clear()
    listening = false
  }

  /**
   * Starts the reporter.
   */
  def start(): Unit = {
    if (!listening) sentryRegistry.addListener(this)
    listening = true
  }

  private def registerBean(name: String, bean: JmxReporter.SentryMBean, objectName: ObjectName): Unit = {
    server.registerMBean(bean, objectName)
    registeredBeans.put(name, objectName)
  }

  private def unregisterBean(objectName: ObjectName): Unit = {
    try {
      server.unregisterMBean(objectName)
    } catch {
      case e: InstanceNotFoundException =>
        // This is often thrown when the process is shutting down. An application with lots of
        // sentries will often begin unregistering sentries *after* JMX itself has cleared,
        // resulting in a huge dump of exceptions as the process is exiting.
        logger.trace("Error unregistering {}", Array(objectName, e))
      case e: MBeanRegistrationException =>
        logger.debug("Error unregistering {}", Array(objectName, e))
    }
  }
}

object JmxReporter {
  trait SentryMBean {
    def reset(): Unit
  }
  class Sentry(val sentry: nl.grons.sentries.support.Sentry) extends SentryMBean {
    def reset(): Unit = { sentry.reset() }
  }

  trait CircuitBreakerSentryMBean extends SentryMBean {
    def trip(): Unit
  }
  class CircuitBreakerSentry(sentry: nl.grons.sentries.core.CircuitBreakerSentry) extends Sentry(sentry) with CircuitBreakerSentryMBean {
    def trip(): Unit = { sentry.trip() }
  }

  trait AdaptiveThroughputSentryMBean extends SentryMBean {
    def trip(): Unit
  }
  class AdaptiveThroughputSentry(sentry: nl.grons.sentries.core.AdaptiveThroughputSentry) extends Sentry(sentry) with AdaptiveThroughputSentryMBean {
    def trip(): Unit = { sentry.trip() }
  }
}
