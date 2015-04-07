/*
 * Sentries
 * Copyright (c) 2012-2015 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the BSD 2-Clause License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

package nl.grons.sentries.examples

import scala.util.control.Exception._
import java.util.concurrent.{Future, ExecutionException, Callable, Executors, TimeUnit}
import scala.collection.JavaConverters._
import nl.grons.sentries.SentrySupport
import nl.grons.sentries.support._
import scala.util.Random
import nl.grons.sentries.core.LoadBalancer
import scala.concurrent.duration.Duration

/**
 * Some runnable examples to show how to use sentries.
 */
object SentryExampleApp extends App {

  new JmxReporter().start()

  val random = new Random()

  /**
   * This is the resource we're going to call in the examples.
   */
  class SimpleExampleService(val name: String) {
    /**
     * Throws a [[java.lang.NullPointerException]] when parameter is null.
     */
    def doIt(param: String): Int = {
      param.length % 2
    }
  }

  /**
   * Simple example.
   */
  class Example1 extends SentrySupport {
    /**
     * Define a sentry that collects metrics combined with a fail limit (aka circuit breaker).
     *
     * The sentry will 'break' when 2 consecutive invocations failed.
     * When in broken state, after each 50 ms, 1 attempt will be made to go back to the normal ('flow') state.
     */
    private[this] val simpleServiceSentry = sentry("simpleExampleService").
      withMetrics.
      withFailLimit(failLimit = 2, retryDelay = Duration(500, TimeUnit.MILLISECONDS))

    /**
     * The next circuit breaker works independently from serviceCb.
     * Always use an independent sentry for a resource that fails independently.
     *
     * This sentry also denies invocations when there are already 10 in progress (concurrency limit).
     */
    private[this] val anotherServiceSentry = sentry("anotherService").
      withFailLimit(failLimit = 4, retryDelay = Duration(1000, TimeUnit.MILLISECONDS)).
      withConcurrencyLimit(10)

    /**
     * The service we are going to call.
     */
    private[this] val service: SimpleExampleService = new SimpleExampleService("ses")

    /**
     * Here we use the sentry by passing it a code block.
     */
    def callService(param: String): Int = simpleServiceSentry {
      service.doIt(param)
    }
  }

  // Lets try it out!
  val example1 = new Example1
  // First failure. The exception is simply rethrown. The sentry's failure counter increases to 1:
  assertThrows[NullPointerException] { example1.callService(null) }
  // Next attempt succeeds, failure counter back to 0:
  assert( example1.callService("abc") == 1 )
  // Again a failure. The exception is rethrown and the failure counter increases to 1:
  assertThrows[NullPointerException] { example1.callService(null) }
  // Second failure in a row. The exception is still rethrown:
  assertThrows[NullPointerException] { example1.callService(null) }
  // however, the failure counter is now at 2 and the sentry goes to the 'broken' state.

  // Next attempt to use the service; a NotAvailableException is thrown immediately.
  // Note that SimpleExampleService is not called even though it would work this time.
  assertThrows[NotAvailableException] { example1.callService("abc") }

  // The circuit breaker will retry after 50 ms. Lets wait for it.
  Thread.sleep(505L)
  // Yes, the sentry is flowing again!
  assert( example1.callService("abcd") == 0 )

  /**
   * Load balancer example.
   *
   * In this example we'll show the load balancer support. The default load balancer requires you to implement
   * 'resources' that returns all available resources. When this is not dynamic, simply implement 'resources'
   * as a val (as done in this example).
   *
   * Upon invoking the load balancer it randomly picks one of the given resources and tries to use it. When a
   * NotAvailable exception is caught, the next resource is attempted.
   *
   * If the resource throws any other error, the error is simply rethrown and not further attempt to invoke
   * another resource is done.
   */
  class Example2 extends SentrySupport {
    /**
     * LoadBalancer is abstract and requires us to implement 'resources'.
     */
    val loadBalancer = new LoadBalancer[SimpleExampleService]("load balancer") {
      /**
       * 'resources' given the current set of resource and their sentries for each invocation of the load balancer.
       * So if you're doing fancy stuff like remote look-ups (for example in Zookeeper) you might want to consider
       * caching the result or mixin the (TODO) [[Caching]] trait.
       *
       * Another solution is to implement 'resources' as a val, as we do here.
       *
       * In this example we have 3 resources available. Each resource will be put in a pair with a sentry for
       * that resource.
       */
      val resources = IndexedSeq(
        new SimpleExampleService("s1"),
        new SimpleExampleService("s2"),
        new SimpleExampleService("s3")
      ).map(addSentry(_))

      /**
       * Puts the resource (in this case a SimpleExampleService) in a pair with its sentry.
       */
      def addSentry(ps: SimpleExampleService): (SimpleExampleService, NamedSentry) =
        (ps, sentry(ps.name).withMetrics.withConcurrencyLimit(2).withFailLimit(10, Duration(500, TimeUnit.MILLISECONDS)))
    }

    /**
     * Here we use the load balancer.
     *
     * Note that the code block will be executed in the context of the resource's sentry. Note that the code might
     * be invoked multiple times when a NotAvailableException is caught in the load balancer.
     *
     * You can also use this fact to your advantage; if inside the code block it becomes clear that another
     * resource should be called, explicitly throw a NotAvailableException and the load balancer will invoke
     * the next resource.
     */
    def callService(param: String): Int = loadBalancer { ps: SimpleExampleService =>
      Thread.sleep(50 + random.nextInt(100)) // simulating a slow computation....
      ps.doIt(param)
    }
  }

  // Great! Now lets try to use it:
  val example2 = new Example2
  // The task we are going to call concurrently:
  val task = new Callable[Int] {
    def call() = {
      Thread.sleep(10 + random.nextInt(10))
      example2.callService("abcd")
    }
  }
  val tasks = Vector.fill(500)(task).asJava

  // There are 3 resource, each protected with a sentry that allows 2 simultaneous invocations, we can therefore
  // (in theory) safely do 6 invocations concurrently.
  // In practice, when threads behave according to the herd-effect multiple threads can race for the same resource.
  // As each resource is only tried once, any thread might miss a resource that became available just after its
  // use was attempted. To see how this works, just replace the duration in [[Example2.callService(String)]]
  // with a fixed delay.
  {
    val executor = Executors.newFixedThreadPool(6)
    try {
      val futures = executor.invokeAll(tasks)
      val options = futuresToOptions(futures)

      // Assert that there are no None's
      val misses = options.count(_.isEmpty)
      assert(misses == 0, "Expected no misses, there were " + misses)

    } finally {
      executor.shutdown()
    }
  }

  // Now lets try to break it by calling it from many more threads.
  while (true) {
    val executor = Executors.newFixedThreadPool(50)
    try {
      val futures = executor.invokeAll(tasks)
      val options = futuresToOptions(futures)

      // Assert that there are some None's
      val misses = options.count(_.isEmpty)
      assert(misses > 0, "Expected some misses, there were 0")

    } finally {
      executor.shutdown()
    }
  }

  /**
   * Little helper method to assert that the given code throws an exception of given type.
   */
  def assertThrows[E <: Throwable](c: => Unit)(implicit m: Manifest[E]) {
    val e = allCatch.either(c)
    assert(e.isLeft && m.erasure.isAssignableFrom(e.left.get.getClass))
  }

  // Convert normal Future results to a Some, and NotAvailableExceptions to a None:
  def futuresToOptions[A](futures: java.util.List[Future[A]]): Seq[Option[A]] = futures.asScala.map { future =>
    try Some(future.get())
    catch {
      case e: ExecutionException if e.getCause.isInstanceOf[NotAvailableException] => None
    }
  }

}
