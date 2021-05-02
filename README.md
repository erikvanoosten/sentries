[![No Maintenance Intended](http://unmaintained.tech/badge.svg)](http://unmaintained.tech/)

*Update May 2021:* This project was started for use in a completely synchronous environment.
Even back then this was a questionable idea given the high load we had to deal with. Instead
of using this project, I would recommend you to migrate to something more asynchronous such
as Zio or Cats Effect. If that is out of the question, feel free to clone this project and
make it live on.

-----

# Sentries

Sentries is an out-of-your way Scala library that will handle all the fault-handling around calling resources like databases and remote services.

Sentries is located at: `"nl.grons" %% "sentries" % "0.8.0"`

*Documentation*

* [Download (Sbt, Maven)](docs/download.md)
* [Available sentries](docs/sentries.md)
* [Testing support](docs/testing.md)
* [Advanced sentry chaining](docs/chaining.md)
* [Sentries and Metrics](docs/metrics.md) TODO
* [Writing you own Sentry](docs/writing-sentries.md) TODO
* [Developing sentries](docs/developing-sentries.md)

# Introduction

> **sentry** (pl. **sentries**) a soldier stationed to keep guard or to control access to a place.

Sentries provides known techniques such as the Circuit Breaker, rate limiting, slow ramp up, load balancing (not yet stable) and retries (todo). You select what you need by composing several sentries in a new sentry, a sentry chain. By combining this with metrics and JMX control, Sentries is the ideal wrapper for invoking databases, remote services, etc.

Example usage:
```scala
class DoItAllService extends nl.grons.sentries.support.SentrySupport {

  // withFailLimit == circuit breaker
  val dbSentry = sentry("mysql:localhost:3366").
          withMetrics.
          withFailLimit(failLimit = 5, retryDelay = 500 milliseconds)
  val twitterApiSentry = sentry("twitter").
          withMetrics.
          withConcurrencyLimit(3)

  def loadTweetFromDb(id: Long): Tweet = dbSentry {
    database.load(id)
  }

  def getFromTwitter(id: Long): Tweet = twitterApiSentry {
    twitterApi.load(id)
  }
}
```

See [Available sentries](docs/sentries.md) for more and [SentryExampleApp](/src/main/scala/nl/grons/sentries/examples/SentryExampleApp.scala) for a more elaborate example.

## JMX

JMX control is started with the following:

```scala
new nl.grons.sentries.support.JmxReporter().start()
```

## Usage guidelines

* Exceptions are always rethrown. (Retry will be an exception to this rule.)
* When a sentry needs to throw an exception, it will throw a `NotAvailableException` or subtype.
* Sentries ignore `NotAvailableException`s from other sentries.
* Sentries assume that a `ControlThrowable` means success. (These are used by Scala to do flow control.)
* Sentries are fully multi-thread safe. Coordination with other threads is kept to the minimum. In addition,
  sentries will never block. If an operation can not be performed immediately, it will throw a `NotAvailableException`.
* Sentries are singletons, the builder checks each sentry against the registry before usage. The registry stores sentries by owner type, resource name and sentry type.
* Building a sentry chain is easiest by mixing in `SentrySupport` and use method `sentry` as in the example above.
* It is permitted to use the same sentry in multiple sentry chains.
* The sentry that limits durations should NOT be used from a `Future` or from an `Actor`. Futures and actors have other mechanisms to deal with timeouts that are more suited. (If possible this will be resolved in a later version.)
