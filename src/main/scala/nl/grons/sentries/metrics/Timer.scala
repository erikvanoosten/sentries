/*
 * Copyright 2010-2012 Coda Hale and Yammer, Inc.
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
package nl.grons.sentries.metrics

import java.util.concurrent.TimeUnit

/**
 * A Scala façade class for Timer.
 */
class Timer(metric: com.yammer.metrics.core.Timer) {
  /**
   * Runs f, recording its duration, and returns the result of f.
   */
  def time[A](f: => A): A = {
    val ctx = metric.time
    try {
      f
    } finally {
      ctx.stop
    }
  }

  /**
   * Adds a recorded duration.
   */
  def update(duration: Long, unit: TimeUnit) {
    metric.update(duration, unit)
  }

  /**
   * Returns a timing [[com.metrics.yammer.core.TimerContext]],
   * which measures an elapsed time in nanoseconds.
   */
  def timerContext() = metric.time()

  /**
   * Returns the number of durations recorded.
   */
  def count = metric.count

  /**
   * Clears all recorded durations.
   */
  def clear() {
    metric.clear()
  }

  /**
   * Returns the longest recorded duration.
   */
  def max = metric.max

  /**
   * Returns the shortest recorded duration.
   */
  def min = metric.min

  /**
   * Returns the arithmetic mean of all recorded durations.
   */
  def mean = metric.mean

  /**
   * Returns the standard deviation of all recorded durations.
   */
  def stdDev = metric.stdDev

  /**
   * Returns a snapshot of the values in the timer's sample.
   */
  def snapshot = metric.getSnapshot

  /**
   * Returns the timer's rate unit.
   */
  def rateUnit = metric.rateUnit

  /**
   * Returns the timer's duration unit.
   */
  def durationUnit = metric.durationUnit

  /**
   * Returns the type of events the timer is measuring.
   */
  def eventType = metric.eventType

  /**
   * Returns the fifteen-minute rate of timings.
   */
  def fifteenMinuteRate = metric.fifteenMinuteRate

  /**
   * Returns the five-minute rate of timings.
   */
  def fiveMinuteRate = metric.fiveMinuteRate

  /**
   * Returns the mean rate of timings.
   */
  def meanRate = metric.meanRate

  /**
   * Returns the one-minute rate of timings.
   */
  def oneMinuteRate = metric.oneMinuteRate
}

