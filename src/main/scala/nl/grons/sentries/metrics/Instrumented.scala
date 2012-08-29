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

import com.yammer.metrics.Metrics

/**
 * The mixin trait for creating a class which is instrumented with metrics.
 */
trait Instrumented {
  private lazy val metricsGroup = new MetricsGroup(getClass, metricsRegistry)

  /**
   * Returns the MetricsGroup for the class.
   */
  def metrics = metricsGroup

  /**
   * Returns the MetricsRegistry for the class.
   */
  def metricsRegistry = Metrics.defaultRegistry()
}

