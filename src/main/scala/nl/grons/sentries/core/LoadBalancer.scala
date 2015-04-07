/*
 * Sentries
 * Copyright (c) 2012-2015 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the BSD 2-Clause License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

package nl.grons.sentries.core

import nl.grons.sentries.support.{NotAvailableException, NamedSentry}
import scala.util.Random
import scala.annotation.tailrec

/**
 * Does load balancing between several resources.
 *
 * Warning: the API is definitely not yet stable.
 * When used wrong, you cause a memory leak.
 * Use at your own risk.
 *
 * @param loadBalancerName name of the load balancer
 * @param reporter call back function invoked when a resource failed but more resources can be tried,
 *                 defaults to doing nothing. First argument is name of the failed resource, second argument
 *                 is the caught exception
 * @tparam R type of resource
 */
abstract class LoadBalancer[R](loadBalancerName: String, val reporter: (String, NotAvailableException) => Unit = (_, _) => () ) {
  // NOTE: when using Java 8 override with ThreadLocalRandom.current()
  val random = new Random()

  def resources: IndexedSeq[(R, NamedSentry)]

  def apply[T](r: R => T): T = {
    val allResources = resources
    if (allResources.isEmpty) throw new NoResourcesAvailableException(loadBalancerName, "No resources available")

    @tailrec def tryResource(nextIndex: Int, attemptsLeft: Int): T = {
      val (resource, sentry) = allResources(nextIndex)
      val t = try {
        Some(sentry(r(resource)))
      } catch {
        case e: NotAvailableException if attemptsLeft > 1 =>
          reporter(sentry.resourceName, e)
          None
      }
      t match {
        case Some(x) => x
        case None => tryResource((nextIndex + 1) % allResources.size, attemptsLeft - 1)
      }
    }

    val startIndex = if (allResources.size == 1) 0 else random.nextInt(allResources.size)
    tryResource(startIndex, allResources.size)
  }
}

// Please don't use this yet.
//// TODO: document initialization order in case cacheDuration is overridden
//trait Caching[R] extends LoadBalancer[R] {
//  val cacheDuration = 5000L
//  private[this] val cache = new AtomicReference(super.resources)
//  private[this] val nextCacheExpiration = new AtomicLong(System.currentTimeMillis() + cacheDuration)
//
//  override abstract def resources: IndexedSeq[(R, NamedSentry)] = {
//    val now: Long = System.currentTimeMillis()
//    val nextExp: Long = nextCacheExpiration.get
//    if (now >= nextExp && nextCacheExpiration.compareAndSet(nextExp, now + cacheDuration)) {
//      val updatedResources = super.resources
//      cache.set(updatedResources)
//      updatedResources
//    } else {
//      cache.get
//    }
//  }
//}

// Please don't use this yet.
//// TODO: document initialization order in case initialCacheDuration is overridden
//trait VariableCaching[R] extends LoadBalancer[R] {
//  val initialCacheDuration = 5000L
//  val minimumCacheDuration = 100L
//  private[this] val cache = new AtomicReference(super.resources)
//  private[this] val nextCacheExpiration = new AtomicLong(System.currentTimeMillis() + initialCacheDuration)
//  private[this] val currentCacheDuration = new AtomicLong(initialCacheDuration)
//
//  override abstract def resources: IndexedSeq[(R, NamedSentry)] = {
//    val now: Long = System.currentTimeMillis()
//    val nextExp: Long = nextCacheExpiration.get
//    if (now >= nextExp && nextCacheExpiration.compareAndSet(nextExp, now + currentCacheDuration.get)) {
//      val updatedResources = super.resources
//      cache.set(updatedResources)
//      updatedResources
//    } else {
//      cache.get
//    }
//  }
//
//  override abstract def apply[T](r: R => T): T = {
//    try {
//      val t = super.apply(r)
//      currentCacheDuration.set(initialCacheDuration)
//      t
//    } catch {
//      case e: NotAvailableException =>
//        val c = currentCacheDuration.get
//        val updated = ((c * 2) / 3).max(minimumCacheDuration)
//        if (c != updated) currentCacheDuration.compareAndSet(c, updated)
//        throw e
//    }
//  }
//}

/**
 * No resources present at all, invocations are failing immediately.
 */
class NoResourcesAvailableException(resourceName: String, message: String, cause: Throwable = null)
  extends NotAvailableException(resourceName, message, cause)
