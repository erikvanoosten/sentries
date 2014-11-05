# Available sentries

Below are descriptions of sentries that are ready for production usage.

## Metrics

A sentry that times invocations and meters failures.

Implemented by [MetricSentry](/src/main/scala/nl/grons/sentries/core/MetricSentry.scala).

One timer and 2 meter metrics are registered: "all", "fail" and "notAvailable".

The "all" timer times and counts all invocations, the "fail" meter counts invocations that threw an exception, and the "notAvailable" meter counts invocations that were blocked by a sentry that is later in the chain (detected by catching `nl.grons.sentries.support.NotAvailableException`s).

When only a timer is needed, use the Timer sentry instead.

To count the 'not available' invocations, this must be the first sentry in the chain.

## Timer

A sentry that times invocations.

Implemented by [TimerSentry](/src/main/scala/nl/grons/sentries/core/TimerSentry.scala).

One timer is registered: "all". It is updated for each invocation. For more extensive measuring, use the Metrics sentry instead.

## Circuit Breaker (fail limiter)

A sentry that limits the number of consecutive failures; a.k.a. a circuit breaker.

Implemented by [CircuitBreakerSentry](/src/main/scala/nl/grons/sentries/core/CircuitBreakerSentry.scala).

The goal of a circuit breaker is to protect the caller from a resource that fails. It also protects the resource from overload when it is trying to recover. A circuit breaker works by keeping track of the number of consecutive failures. When there are more then consecutive `failLimit` failures, the circuit breaker 'breaks' and pro-actively blocks all following calls by throwing a [CircuitBreakerBrokenException](/src/main/scala/nl/grons/sentries/core/CircuitBreakerBrokenException.scala).

Every `retryDelay` _one_ invocation is allowed through in order to test the resource. When this call succeeds, the circuit breaker goes back to the flow state. If not, it stays in the broken state.

Please see <http://day-to-day-stuff.blogspot.com/2013/02/breaking-circuit-breaker.html> for a rationale of the used vocabulary (broken/flow state vs. the more known open/half open/close state).

## Adaptive throughput

A sentry that adapts throughput with the success ratio of invoking the protected resource. Think of it as a gradual circuit breaker.

Implemented by [AdaptiveThroughputSentry](/src/main/scala/nl/grons/sentries/core/AdaptiveThroughputSentry.scala).

The goal of this sentry is to protect the caller from a resource that slows down, or starts to produce errors when overloaded. By reducing throughput until success ratio is at an expected level, the resource can recover and work at its optimal efficiency.

For resources that behave correct most of the time, the target success ratio can be set quite high, e.g. `0.95`. When exceptions are more 'normal', you may have to lower this parameter.

The success ratio is calculated per `evaluationDelay` with a simple `1 - (fail count / success count)`.
When the success ratio is below `targetSuccessRatio`, the throughput is reduced to `currentSuccessRatio * currentThroughputRatio`. When the success ratio is again equal to or above the target ratio, throughput is increased by `successIncreaseFactor` (defaults to +20%) with a minimum of 0.001 (1 in thousand calls may proceed).
Note that regardless of the `currentThroughputRatio`, at least 1 call per evaluation period is allowed to continue.

This sentry only makes sense for high volume resources. To prevent throttling in low volume times, it is possible to set the minimum number of invocations that must be observed per `evaluationDelay` before throttling takes place. (See the `minimumInvocationCountThreshold` parameter).

It does not make sense to throttle on fast failing invocations. In those cases its better to get the exception from the underlying resource then to get a `ReducedThroughputException`. Parameter `failedInvocationDurationThreshold` (introduced in version 0.8.0) sets the minimum duration of failed invocation in order for those invocations to be counted as failed.
By setting this value to a non-zero value, fast failures do not reduce throughput. The default is `0` for backward compatibility.
However, when the builder is used to construct the sentry, the default `failedInvocationDurationThreshold` is `300 nanoseconds`.

When there is a calamity, this sentry only reacts as fast as the given `evaluationDelay` (1 second by default).
When the resource becomes fully available, it takes at most 39 evaluation before throughput is back at 100%. You can test this by evaluating the following code in a Scala REPL:

```scala
scala> val successIncreaseFactor = 1.2D
successIncreaseFactor: Double = 1.2

scala> Stream.iterate(0.0D)(x => (x * successIncreaseFactor).max(0.001).min(1.0D)).zipWithIndex.takeWhile(_._1 < 1.0).last._2 + 1
res0 Int = 39
```

## Concurrency limiter

A sentry that limits the number of concurrent invocations.

Implemented by [ConcurrencyLimitSentry](/src/main/scala/nl/grons/sentries/core/ConcurrencyLimitSentry.scala).

The goal of a concurrency limiter is to prevent overloading of a shared resource. This sentry can be used as an alternative to a pool for easy to crate objects.

## Rate limiter

A sentry that limits the number of invocations per time span.

Implemented by [RateLimitSentry](/src/main/scala/nl/grons/sentries/core/RateLimitSentry.scala).


A rate limiter is useful for cases where you asynchronously hand of some work and you don't want to overload the receiver. For example sending error emails or an asynchronous rendering job for which there is a limited capacity.
For other cases a concurrency limiter is usually more appropriate.

## Duration limiter

A sentry that limits the duration of an invocation.

Implemented by [DurationLimitSentry](/src/main/scala/nl/grons/sentries/core/DurationLimitSentry.scala).

The goal of a duration limiter is to support callers that are only interested in the results for a limited time.

WARNING: do NOT use this sentry when you invoke it from a `Future` or an `Actor`. For such circumstances you are MUCH better of with a timeout on the enclosing future or a timeout message within the actor. Reason: this sentry blocks the current thread while waiting on a future that executes the task. Blocking the current thread is an anti-pattern for futures and actors.

Note that when the wait period has passed, the task still completes in another thread. Make sure there are enough threads in the executor. By default a `Executors.newCachedThreadPool()` is used which creates as much threads as needed. The executor can be changed by overriding field `executionContext`.
