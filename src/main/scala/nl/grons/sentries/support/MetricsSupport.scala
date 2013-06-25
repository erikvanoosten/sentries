package nl.grons.sentries.support

import com.yammer.metrics.core.Gauge

/**
 * Helper methods to convert expressions to gauges.
 */
object MetricsSupport {

  implicit def function0ToGauge[T](expr: => T): Gauge[T] = new Gauge[T] {
    def value() = expr
  }

}
