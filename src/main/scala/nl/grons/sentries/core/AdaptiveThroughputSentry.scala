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

import com.yammer.metrics.core.{MetricName, HealthCheck}
import com.yammer.metrics.{Metrics, HealthChecks}
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import nl.grons.sentries.support.{LongAdder, NotAvailableException, ChainableSentry}
import nl.grons.sentries.support.MetricsSupport._
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.util.control.ControlThrowable

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
 * throughput is increased by `successIncreaseFactor` (defaults to +20%) with a minimum of 0.0001D (1 in thousand calls
 * may proceed).
 * Note that regardless of the `currentThroughputRatio`, at least 1 call per evaluation period is allowed to continue.
 *
 * This sentry only makes sense for high volume resources. To prevent throttling in low volume times, it is possible
 * to set the minimum number of invocations that must be observed per `evaluationDelay` before throttling takes place.
 * (see the `minimumInvocationCountThreshold` parameter).
 *
 * It does not make sense to throttle on fast failing invocations. In those cases its better to get the exception
 * from the underlying resource then to get a [[ReducedThroughputException]]. Parameter `failedInvocationDurationThreshold`
 * sets the minimum duration of failed invocation in order for those invocations to be counted as failed. By setting
 * this value to a non-zero value, fast failures do not reduce throughput. (Note that the default is `0` for backward
 * compatibility.)
 *
 * When there is a calamity, this sentry only reacts as fast as the given `evaluationDelay` (1 second by default).
 * When the resource becomes fully available, it takes at most 39 evaluation before throughput is back at 100%.
 * You can test this by evaluating the following code in a Scala REPL:
 * {{{
 * scala> val successIncreaseFactor = 1.2D
 * successIncreaseFactor: Double = 1.2
 *
 * scala> Stream.iterate(0.0D)(x => (x * successIncreaseFactor).max(0.001).min(1.0D)).zipWithIndex.takeWhile(_._1 < 1.0).last._2 + 1
 * res0 Int = 39
 * }}}
 *
 * A new instance can be obtained through the [[nl.grons.sentries.SentrySupport]] mixin.
 *
 * @param owner the owner class of this sentry
 * @param resourceName name of the resource
 * @param targetSuccessRatio target success ratio, `0 < targetSuccessRatio < 1`, defaults to `0.95D`
 * @param evaluationDelay the time between calculations of the current throughput, defaults to 1 second
 * @param minimumInvocationCountThreshold the minimum number of invocations that must be observed per `evaluationDelay`
 *   before invocations are throttled, defaults to `0` (>=0)
 * @param successIncreaseFactor factor to apply to current throughput ratio, `successIncreaseFactor > 1`, defaults to 1.2D
 * @param failedInvocationDurationThreshold the minimum duration for a failed invocation to be counted as failed,
 *   for backward compatibility this defaults to 0 milliseconds. A better default would be a small number,
 *   e.g. `1 millisecond`.
 */
class AdaptiveThroughputSentry(
  owner: Class[_],
  val resourceName: String,
  val targetSuccessRatio: Double = 0.95D,
  val evaluationDelay: FiniteDuration = Duration(1, TimeUnit.SECONDS),
  val minimumInvocationCountThreshold: Int = 0,
  successIncreaseFactor: Double = 1.2D,
  val failedInvocationDurationThreshold: FiniteDuration = Duration(0, TimeUnit.MILLISECONDS)
) extends ChainableSentry {
  import AdaptiveThroughputSentry._

  require(targetSuccessRatio > 0 && targetSuccessRatio < 1, "0 < targetSuccessRatio < 1 but is " + targetSuccessRatio)
  require(successIncreaseFactor > 1.0, "successIncreaseFactor > 1 but is " + successIncreaseFactor)
  require(minimumInvocationCountThreshold >= 0, "minimumInvocationCountThreshold >= 0 but is " + minimumInvocationCountThreshold)

  val sentryType = "failRatioLimit"

  private[this] val state = new AtomicReference[State](new State(this, 1.0D))
  private[this] val failedInvocationDurationThresholdNanos = failedInvocationDurationThreshold.toNanos

  HealthChecks.register(new HealthCheck(new MetricName(owner, constructName()).getMBeanName) {
    def check() = {
      val ctr = throughputRatio
      if (ctr == 1.0) HealthCheck.Result.healthy
      else HealthCheck.Result.unhealthy("throughput limited to " + (ctr * 100).toInt + "%")
    }
  })

  Metrics.newGauge(owner, constructName("throughputRatio"), throughputRatio)
  Metrics.newGauge(owner, constructName("failRatio"), failRatio)

  def apply[T](r: => T): T = {
    state.get.preInvoke()
    val start = System.nanoTime()
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
      case e: Throwable =>
        // Fast failures are not interesting to block, do not update state.
        val duration = System.nanoTime() - start
        if (duration >= failedInvocationDurationThresholdNanos) state.get.onThrowable(e)
        throw e
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
  def throughputRatio: Double = state.get.throughputRatio

  /**
   * The current fail ratio (0 <= ratio <= 1).
   */
  def failRatio: Double = 1.0 - state.get.successRatio

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
  class State(ats: AdaptiveThroughputSentry, val throughputRatio: Double) {
    private[this] val nextEvaluationAt: Long = System.currentTimeMillis() + ats.evaluationDelay.toMillis
    private[this] val callCount = new LongAdder
    private[this] val failCount = new LongAdder

    def preInvoke() {
      // The call may proceed under the following conditions:
      // - it is time to evaluate the next state and this thread is the first to do so OR
      // - throughputRatio is 1.0 OR
      // - throughputRatio is below 1.0 and our lucky number is below throughputRatio.
      // Otherwise an exception is thrown.
      val evaluate = System.currentTimeMillis > nextEvaluationAt
      if (!(evaluate && ats.attemptNextState(this, nextThroughputRatio)) && throughputRatio < 1.0) {
        val luckyNumber = ThreadLocalRandom.current().nextDouble()
        if (luckyNumber > throughputRatio)
          throw new ReducedThroughputException(ats.resourceName,
            "%s has reduced throughput because success ratio is below %d%%, current throughput is %d%%".format(
              ats.resourceName, (ats.targetSuccessRatio * 100).toInt, (throughputRatio * 100).toInt))
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

    def successRatio: Double = {
      val cc = callCount.doubleValue()
      if (cc == 0.0)
        // Don't do anything when there were no invocations. This also prevents divide by zero errors.
        Double.NaN
      else if (cc <= ats.minimumInvocationCountThreshold)
        1.0D
      else
        // Due to concurrency, the calculation might result in values below 0 or above 1. The min/max compensate.
        (1.0D - failCount.doubleValue() / cc).min(1.0D).max(0.0D)
    }

    private def nextThroughputRatio: Double = {
      val sr = successRatio
      val next = if (sr.isNaN) {
        // Don't change throughput ratio.
        throughputRatio
      } else if (sr < ats.targetSuccessRatio) {
        // Decrease throughput by current success ratio.
        (sr * throughputRatio).max(0.0D)
      } else {
        // Increase throughput with 20% (to at least 0.001).
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
