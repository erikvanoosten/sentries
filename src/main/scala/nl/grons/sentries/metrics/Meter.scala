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

/**
 * A Scala façade class for Meter.
 */
class Meter(metric: com.yammer.metrics.core.Meter) {

  /**
   * Marks the occurrence of an event.
   */
  def mark() {
    metric.mark()
  }

  /**
   * Marks the occurrence of a given number of events.
   */
  def mark(count: Long) {
    metric.mark(count)
  }

  /**
   * Returns the meter's rate unit.
   */
  def rateUnit = metric.rateUnit

  /**
   * Returns the type of events the meter is measuring.
   */
  def eventType = metric.eventType

  /**
   * Returns the number of events which have been marked.
   */
  def count = metric.count

  /**
   * Returns the fifteen-minute exponentially-weighted moving average rate at
   * which events have occurred since the meter was created.
   * <p>
   * This rate has the same exponential decay factor as the fifteen-minute load
   * average in the top Unix command.
   */
  def fifteenMinuteRate = metric.fifteenMinuteRate

  /**
   * Returns the five-minute exponentially-weighted moving average rate at
   * which events have occurred since the meter was created.
   * <p>
   * This rate has the same exponential decay factor as the five-minute load
   * average in the top Unix command.
   */
  def fiveMinuteRate = metric.fiveMinuteRate

  /**
   * Returns the mean rate at which events have occurred since the meter was
   * created.
   */
  def meanRate = metric.meanRate

  /**
   * Returns the one-minute exponentially-weighted moving average rate at
   * which events have occurred since the meter was created.
   * <p>
   * This rate has the same exponential decay factor as the one-minute load
   * average in the top Unix command.
   */
  def oneMinuteRate = metric.oneMinuteRate
}

