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

import java.util.concurrent.atomic.AtomicReference
import com.yammer.metrics.core.{MetricName, HealthCheck, Gauge}
import com.yammer.metrics.{Metrics, HealthChecks}
import nl.grons.sentries.support.{LongAdder, NotAvailableException, ChainableSentry}
import nl.grons.sentries.cross.Concurrent.Duration
import scala.util.control.ControlThrowable
import java.util.concurrent.TimeUnit
import scala.concurrent.forkjoin.ThreadLocalRandom

/**
 * A sentry that adapts throughput with the success ratio of invoking the protected resource. Think of it as
 * a gradual circuit breaker.
 *
 * The goal of this sentry is to protect the caller from a resource that slows down, or starts to produce errors
 * when overloaded. By reducing throughput until success ratio is at an expected level, the resource can recover
 * and work at its optimal efficiency.
 *
 * For resources that behave correct most of the time, the target success ratio can be set quite high, e.g. `0.95`.
 * When exceptions are more 'normal', you may have to lower this parameter.
 *
 * The success ratio is calculated per `evaluationDelay` with a simple `1 - (fail count / success count)`.
 * When the success ratio is below `targetSuccessRatio`, the throughput is reduced to
 * `currentSuccessRatio * currentThroughputRatio`. When the success ratio is again equal to or above the target ratio,
 * throughput is increased by 20% with a minimum of 0.0001D (1 in thousand calls may proceed).
 *
 * When there is a calamity, this sentry only reacts as fast as the given `evaluationDelay`, typically 1 second.
 * When the resource becomes fully available, it takes about 50 evaluation before throughput is back at 100%.
 *
 * A new instance can be obtained through the [[nl.grons.sentries.SentrySupport]] mixin.
 *
 * @param resourceName name of the resource
 * @param targetSuccessRatio target success ratio, `0 < targetSuccessRatio < 1`
 * @param owner the owner class of this sentry
 * @param evaluationDelay the time between calculations of the current throughput, defaults to 1 second
 */
class AdaptiveThroughputSentry(
  val resourceName: String,
  val targetSuccessRatio: Double,
  owner: Class[_],
  val evaluationDelay: Duration = Duration(1, TimeUnit.SECONDS)
) extends ChainableSentry {
  import AdaptiveThroughputSentry._

  require(targetSuccessRatio > 0 && targetSuccessRatio < 1, "0 < targetSuccessRatio < 1 but is " + targetSuccessRatio)

  val sentryType = "failRatioLimit"

  private[this] val state = new AtomicReference[State](new State(this, 1.0D))

  HealthChecks.register(new HealthCheck(new MetricName(owner, constructName()).getMBeanName) {
    def check() = {
      val ctr = currentThroughputRatio
      if (ctr == 1.0) HealthCheck.Result.healthy
      else HealthCheck.Result.unhealthy("throughput limited to " + (ctr * 100).toInt + "%")
    }
  })

  Metrics.newGauge(owner, constructName("throughputRatio"), new Gauge[Double] {
    def value = currentThroughputRatio
  })

  def apply[T](r: => T): T = {
    state.get.preInvoke(r _)
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
   * Go back to the initial state.
   */
  def reset() {
    state.set(new State(this, 1.0D))
  }

  /**
   * Reduce throughput to 0.
   */
  def trip() {
    state.set(new State(this, 0.0D))
  }

  /**
   * The current throughput ratio (0 <= ratio <= 1).
   */
  def currentThroughputRatio: Double =
    state.get.throughputRatio

  /**
   * Try to start the next state.
   * @param currentState the expected current state
   * @return true when the state was changed, false when the given state was not the current state
   */
  private def attemptNextState(currentState: State, nextThroughputRatio: Double): Boolean = {
    state.compareAndSet(currentState, new State(this, nextThroughputRatio))
  }

  private def constructName(nameParts: String*) = (Seq(resourceName, sentryType) ++ nameParts).mkString(".")

}

private object AdaptiveThroughputSentry {
  class State(cb: AdaptiveThroughputSentry, val throughputRatio: Double) {
    private[this] val nextEvaluationAt: Long = System.currentTimeMillis() + cb.evaluationDelay.toMillis
    private[this] val callCount = new LongAdder
    private[this] val failCount = new LongAdder

    def preInvoke(r: AnyRef) {
      // The call may proceed under the following conditions:
      // - it is time to evaluate the next state and this thread is the first to do so OR
      // - throughputRatio is 1.0 OR
      // - throughputRatio is below 1.0 and our lucky number is below throughputRatio.
      // Otherwise an exception is thrown.
      val evaluate = System.currentTimeMillis > nextEvaluationAt
      if (!(evaluate && cb.attemptNextState(this, nextThroughputRatio)) && throughputRatio < 1.0) {
        // val luckyNumber = ((System.identityHashCode(r) >> 4) % 1013).toDouble / 1013D
        val luckyNumber = ThreadLocalRandom.current().nextDouble()
        if (luckyNumber > throughputRatio)
          throw new ReducedThroughputException(cb.resourceName,
            "%s has reduced throughput because success ratio is below %d%%, current throughput is %d%%".format(
              cb.resourceName, (cb.targetSuccessRatio * 100).toInt, (throughputRatio * 100).toInt))
      }
      // If no exception was thrown, this invocation may proceed
    }

    def postInvoke() {
      callCount.increment()
    }

    def onThrowable(e: Throwable) {
      callCount.increment()
      failCount.increment()
    }

    private def successRatio: Double = {
      val cc = callCount.doubleValue()
      if (cc == 0.0)
        // Prevent divide by zero
        1.0D
      else
        // Due to concurrency, the calculation might result in values below 0 or above 1. The min/max compensate.
        (1.0D - failCount.doubleValue() / cc).min(1.0D).max(0.0D)
    }

    private def nextThroughputRatio: Double = {
      val sr = successRatio
      val next = if (sr < cb.targetSuccessRatio) {
        // decrease throughput by current success ratio
        (sr * throughputRatio).max(0.0D)
      } else {
        // increase throughput with 20% (to at least 0.001)
        (1.2D * throughputRatio).max(0.001D)
      }
      next.min(1.0D)
    }
  }
}

/**
 * Access to resource was blocked temporarily because throughput is currently reduced.
 */
class ReducedThroughputException(resourceName: String, message: String, cause: Throwable = null)
  extends NotAvailableException(resourceName, message, cause)
