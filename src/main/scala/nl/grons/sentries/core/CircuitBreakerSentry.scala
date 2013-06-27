/*
 * Sentries
 * Copyright (c) 2012-2013 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the BSD 2-Clause License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

package nl.grons.sentries.core

import com.yammer.metrics.{Metrics, HealthChecks}
import com.yammer.metrics.core.{MetricName, HealthCheck}
import java.util.concurrent.atomic.{AtomicReference, AtomicInteger}
import java.util.concurrent.TimeUnit
import nl.grons.sentries.cross.Concurrent.Duration
import nl.grons.sentries.support.{NotAvailableException, ChainableSentry}
import nl.grons.sentries.support.MetricsSupport._
import scala.util.control.ControlThrowable

/**
 * A sentry that limits the number of consecutive failures; a.k.a. a circuit breaker.
 * A new instance can be obtained through the [[nl.grons.sentries.SentrySupport]] mixin.
 */
class CircuitBreakerSentry(
  owner: Class[_],
  val resourceName: String,
  val failLimit: Int,
  val retryDelay: Duration
) extends ChainableSentry {
  import CircuitBreakerSentry._

  val sentryType = "failLimit"

  private[this] val state = new AtomicReference[State](new FlowState(this))
  private[this] val allCallsMeter = Metrics.newMeter(owner, constructName("all"), "invocation", TimeUnit.SECONDS)
  private val blockedCallsMeter = Metrics.newMeter(owner, constructName("blocked"), "invocation", TimeUnit.SECONDS)

  HealthChecks.register(new HealthCheck(new MetricName(owner, constructName()).getMBeanName) {
    def check() = state.get match {
      case _: FlowState => HealthCheck.Result.healthy()
      case _: BrokenState => HealthCheck.Result.unhealthy("broken")
      case _ => HealthCheck.Result.unhealthy("unknown state")
    }
  })

  Metrics.newGauge(owner, constructName("state"), state.get match {
      case _: FlowState => "flow"
      case _: BrokenState => "broken"
      case _ => "unknown"
    })

  def apply[T](r: => T): T = {
    allCallsMeter.mark()
    state.get.preInvoke()
    try {
      val ret = r
      state.get.postInvoke()
      ret

    } catch {
      case e: NotAvailableException =>
        // embedded sentry indicates 'not available', do not update state
        throw e
      case e: ControlThrowable =>
        // Used by Scala for control, it is equivalent to success
        state.get.postInvoke()
        throw e
      case e: Throwable => {
        state.get.onThrowable(e)
        throw e
      }
    }
  }

  /**
   * Switch to broken state.
   */
  def trip() {
    state.set(new BrokenState(this))
  }

  /**
   * Switch to flow state.
   */
  def reset() {
    state.set(new FlowState(this))
  }

  /**
   * Try to restart the broken state.
   * @param currentState the expected current state
   * @return true when the state was changed, false when the given state was not the current state
   */
  def attemptResetBrokenState(currentState: BrokenState): Boolean = {
    state.compareAndSet(currentState, new BrokenState(this))
  }

  private def constructName(nameParts: String*) = (Seq(resourceName, sentryType) ++ nameParts).mkString(".")

}

private object CircuitBreakerSentry {
  abstract class State(cb: CircuitBreakerSentry) {
    def preInvoke()

    def postInvoke()

    def onThrowable(e: Throwable)
  }

  /**
   * CircuitBreaker is flowing, normal operation.
   */
  class FlowState(cb: CircuitBreakerSentry) extends State(cb) {
    private[this] val failureCount = new AtomicInteger

    def preInvoke() {
      /* empty */
    }

    def postInvoke() {
      // Read is less heavy then write, try that first.
      if (failureCount.get() != 0) failureCount.set(0)
    }

    def onThrowable(e: Throwable) {
      val currentCount = failureCount.incrementAndGet
      if (currentCount >= cb.failLimit) cb.trip()
    }
  }

  /**
   * CircuitBreaker is broken. Invocations fail immediately.
   */
  class BrokenState(cb: CircuitBreakerSentry) extends State(cb) {
    private[this] val retryAt: Long = System.currentTimeMillis() + cb.retryDelay.toMillis

    def preInvoke() {
      val retry = System.currentTimeMillis > retryAt
      if (!(retry && cb.attemptResetBrokenState(this))) {
        cb.blockedCallsMeter.mark()
        throw new CircuitBreakerBrokenException(
          cb.resourceName, "Making " + cb.resourceName + " unavailable after " + cb.failLimit + " errors")
      }
      // If no exception is thrown, a retry is started.
    }

    def postInvoke() {
      // Called after a successful retry.
      cb.reset()
    }

    def onThrowable(e: Throwable) {
      /* empty */
    }
  }
}
/**
 * Circuit breaker is in broken state, invocations are failing immediately.
 */
class CircuitBreakerBrokenException(resourceName: String, message: String, cause: Throwable = null)
  extends NotAvailableException(resourceName, message, cause)
