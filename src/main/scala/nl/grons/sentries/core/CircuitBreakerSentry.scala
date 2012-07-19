/*
 * Copyright (c) 2012 Erik van Oosten
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.grons.sentries.core

import java.util.concurrent.atomic.{AtomicReference, AtomicInteger}
import com.yammer.metrics.scala.Instrumented
import com.yammer.metrics.core.{MetricName, HealthCheck, Gauge}
import com.yammer.metrics.HealthChecks
import java.util.concurrent.TimeUnit
import nl.grons.sentries.support.{NotAvailableException, ChainableSentry}
import nl.grons.sentries.core.States._

/**
 * A sentry that limits the number of consecutive failures; a.k.a. a circuit breaker.
 * A new instance can be obtained through the {@link Sentries} mixin.
 */
class CircuitBreakerSentry(
  val resourceName: String,
  val failLimit: Int,
  val retryDelayMillis: Long,
  selfType: Class[_]
) extends ChainableSentry with Instrumented {

  val sentryType = "failLimit"

  private[this] val state = new AtomicReference[State](new FlowState(this))

  HealthChecks.register(new HealthCheck(new MetricName(selfType, constructName()).getMBeanName) {
    def check() = state.get match {
      case _: FlowState => HealthCheck.Result.healthy()
      case _: BrokenState => HealthCheck.Result.unhealthy("broken")
      case _ => HealthCheck.Result.unhealthy("unknown state")
    }
  })

  metricsRegistry.newGauge(selfType, constructName("state"), new Gauge[String] {
    def value = state.get match {
      case _: FlowState => "flow"
      case _: BrokenState => "broken"
      case _ => "unknown"
    }
  })

  val failureMeter = metricsRegistry.newMeter(
    selfType, constructName("failureCount"), "failures", TimeUnit.SECONDS)

  def apply[T](r: => T): T = {
    state.get.preInvoke()
    try {
      val ret = r
      state.get.postInvoke()
      ret

    } catch {
      case e: NotAvailableException =>
        // embedded sentry is not available, do not update state
        throw e
      case e: Throwable => {
        failureMeter.mark()
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

private object States {
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
    private[this] val retryAt: Long = System.currentTimeMillis() + cb.retryDelayMillis

    def preInvoke() {
      val retry = System.currentTimeMillis > retryAt
      if (!(retry && cb.attemptResetBrokenState(this)))
        throw new CircuitBreakerBrokenException(
          cb.resourceName, "Making " + cb.resourceName + " unavailable after " + cb.failLimit + " errors")
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
