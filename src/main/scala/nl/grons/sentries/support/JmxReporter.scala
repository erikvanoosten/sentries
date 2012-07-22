/*
 * Sentries
 * Copyright (c) 2012 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the BSD 2-Clause License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

package nl.grons.sentries.support

import com.yammer.metrics.core.MetricName
import javax.management.{MBeanRegistrationException, InstanceNotFoundException, ObjectName, MBeanServer}
import java.lang.management.ManagementFactory
import scala.collection.mutable
import scala.collection.JavaConverters._
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import nl.grons.sentries

/**
 * A reporter which exposes sentries as JMX MBeans.
 */
class JmxReporter(
  private[this] val sentryRegistry: SentriesRegistry,
  private[this] val server: MBeanServer = ManagementFactory.getPlatformMBeanServer()
) extends SentriesRegistryListener {

  private[this] var listening = false
  private[this] val registeredBeans = newRegisteredBeansMap()
  private[this] val logger = LoggerFactory.getLogger(getClass)


  /**
   * Called when a sentry has been added to the {@link SentriesRegistry}.
   *
   * @param name   the name of the { @link Sentry}
   * @param sentry the { @link Sentry}
   */
  def onSentryAdded(name: MetricName, sentry: NamedSentry) {
    registerBean(name, createMBean(sentry), new ObjectName(name.getMBeanName))
  }

  private def createMBean(sentry: NamedSentry): JmxReporter.SentryMBean = {
    sentry match {
      case s: sentries.core.CircuitBreakerSentry => new JmxReporter.CircuitBreakerSentry(s)
      case s => new JmxReporter.Sentry(s)
    }
  }

  /**
   * Called when a sentry has been removed from the {@link SentriesRegistry}.
   *
   * @param name the name of the { @link Sentry}
   */
  def onSentryRemoved(name: MetricName) {
    unregisterBean(new ObjectName(name.getMBeanName))
  }

  /**
   * Returns a new {@link ConcurrentMap} implementation. Subclass this to do weird things with
   * your own {@link JmxReporter} implementation.
   *
   * @return a new {@link mutable.ConcurrentMap}
   */
  protected def newRegisteredBeansMap(): mutable.ConcurrentMap[MetricName, ObjectName] =
    new ConcurrentHashMap[MetricName, ObjectName](1024).asScala

  def shutdown() {
    sentryRegistry.removeListener(this)
    registeredBeans.values.foreach(unregisterBean(_))
    registeredBeans.clear()
    listening = false
  }

  /**
   * Starts the reporter.
   */
  def start() {
    if (!listening) sentryRegistry.addListener(this)
    listening = true
  }

  private def registerBean(name: MetricName, bean: JmxReporter.SentryMBean, objectName: ObjectName) {
    server.registerMBean(bean, objectName)
    registeredBeans.put(name, objectName)
  }

  private def unregisterBean(objectName: ObjectName) {
    try {
      server.unregisterMBean(objectName)
    } catch {
      case e: InstanceNotFoundException =>
        // This is often thrown when the process is shutting down. An application with lots of
        // sentries will often begin unregistering sentries *after* JMX itself has cleared,
        // resulting in a huge dump of exceptions as the process is exiting.
        logger.trace("Error unregistering {}", objectName, e);
      case e: MBeanRegistrationException =>
        logger.debug("Error unregistering {}", objectName, e);
    }
  }
}

object JmxReporter {
  trait SentryMBean {
    def reset()
  }
  class Sentry(val sentry: nl.grons.sentries.support.Sentry) extends SentryMBean {
    def reset() { sentry.reset() }
  }

  trait CircuitBreakerSentryMBean extends SentryMBean {
    def trip()
  }
  class CircuitBreakerSentry(sentry: nl.grons.sentries.core.CircuitBreakerSentry) extends Sentry(sentry) with CircuitBreakerSentryMBean {
    def trip() { sentry.trip() }
  }
}
