# Available sentries

## Metrics

Implemented by [MetricSentry](src/main/scala/nl/grons/sentries/core/MetricSentry).

One timer and 2 meter metrics are registered: "all", "fail" and "notAvailable".

The "all" timer times and counts all invocations, the "fail" meter counts invocations that threw an exception, and the "notAvailable" meter counts invocations that were blocked by a sentry that is later in the chain (detected by catching `nl.grons.sentries.support.NotAvailableException`s).

When only a timer is needed, use the Timer sentry instead.

To count the 'not available' invocations, this must be the first sentry in the chain.

## Timer

Implemented by [TimerSentry](src/main/scala/nl/grons/sentries/core/TimerSentry).

One timer is registered: "all". It is updated for each invocation. For more extensive measuring, use the Metrics sentry instead.

## Circuit Breaker (fail limiter)

Implemented by [CircuitBreakerSentry](src/main/scala/nl/grons/sentries/core/CircuitBreakerSentry).

## Adaptive throughput

Implemented by [AdaptiveThroughputSentry](src/main/scala/nl/grons/sentries/core/AdaptiveThroughputSentry).

## Concurrency limiter

Implemented by [ConcurrencyLimitSentry](src/main/scala/nl/grons/sentries/core/ConcurrencyLimitSentry).

## Rate limiter

Implemented by [RateLimitSentry](src/main/scala/nl/grons/sentries/core/RateLimitSentry).

## Duration limiter

Implemented by [DurationLimitSentry](src/main/scala/nl/grons/sentries/core/DurationLimitSentry).

WARNING: do NOT use this sentry when you invoke it from a `Future` or an `Actor`. For such circumstances you are MUCH better of with a timeout on the enclosing future or a timeout message within the actor. Reason: this sentry blocks the current thread while waiting on a future that executes the task. Blocking the current thread is an anti-pattern for futures and actors.
