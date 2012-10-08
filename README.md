# Sentries

Sentries is an out-of-your way Scala library that will handle all the fault-handling
around calling resources like databases and remote services.

Sentries provides known techniques such as the Circuit Breaker, rate limiting,
load balancing (not yet stable), slow ramp up (todo) and retries (todo). You select
what you need by composing several sentries in a new sentry, a sentry chain. By combining
this with time measurements and JMX control, Sentries is the ideal wrapper
for invoking databases, remote services, etc.

Example usage:
```scala
class DoItAllService extends nl.grons.sentries.support.SentrySupport {

  val dbSentry = sentry("mysql:localhost:3366") withMetrics withFailLimit(failLimit = 5, retryDelayMillis = 500)
  val twitterApiSentry = sentry("twitter") withMetrics withFailLimit(failLimit = 5, retryDelayMillis = 500) withConcurrencyLimit(3)

  def loadTweetFromDb(id: Long): Tweet = dbSentry {
    database.load(id)
  }

  def getFromTwitter(id: Long): Tweet = twitterApiSentry {
    twitterApi.load(id)
  }
}
```

See [SentryExampleApp](/erikvanoosten/sentries/blob/master/src/main/scala/nl/grons/sentries/examples/SentryExampleApp.scala) for a more elaborate example.

## Get it

SBT:
```
libraryDependencies += "nl.grons" %% "sentries" % "0.4"
```

Maven:
```
<properties>
    <scala.version>2.9.2</scala.version>
</properties>
<dependency>
    <groupId>nl.grons</groupId>
    <artifactId>sentries_${scala.version}</artifactId>
    <version>0.4</version>
</dependency>
```

Sentries is build for scala 2.9.1, 2.9.1-1 and 2.9.2. It will be build for 2.10 as it becomes available.

## Usage guidelines

* Exceptions are always rethrown. (Retry will be an exception to this rule.)
* When a sentry needs to throw an exception, it will throw a `NotAvailableException` or subtype.
* Sentries ignore `NotAvailableException`s from other sentries.
* Sentries assume that a `ControlThrowable` means success. (These are used by Scala to do flow control.)
* Sentries are fully multi-thread safe. Coordination with other threads is kept to the minimum. In addition,
  sentries will never block. If an operation can not be performed immediately, it will throw a `NotAvailableException`.
  Retry, will be the exception to this rule.
* Sentries are singletons, the builder checks each sentry against the registry before usage. The registry stores
  sentries by owner type, resource name and sentry type.
* Building a sentry chain is easiest by mixing in `SentrySupport` and use method `sentry` as in the example above.
* It is permitted to use the same sentry in multiple sentry chains.
* The sentry that limits durations should NOT be used from a `Future` or from an `Actor`. Futures and actors have
  other mechanisms to deal with timeouts that are more suited.

* JMX control is started with the following:
```scala
new nl.grons.sentries.support.JmxReporter().start()
```

## Developer guidelines

* Please use GitHub tracker, or better yet, do pull requests.
* Performance and simplicity is obtained by composing functionality from tiny building blocks.
* If it can't be made friendly to use, then leave it out.
* Code to make the library friendly to use is not part of the core.
* Readability of code triumphs all other considerations.
* If it doesn't perform, make it optional (this basically means that almost every feature is optional).
* Dependencies are avoided. Right now its: JVM 6, Scala 2.9 and Akka 2.0.3 (later: Scala 2.10), Metrics-core 2.1.2.
* Follow effective scala guidelines from Twitter
* Two space indents, use full imports, do not auto-format
* API might break between major versions and before 1.0.0 is reached.

## Sentries in tests

As sentries are effectively singletons, you may have problems testing sentries that keep state (such as the circuit
breaker). To make sure that fresh sentries are created everytime, call the following from a 'before' method. Do make
sure the `clear()` is called *before* you construct the service under test.

```scala
SentrySupport.defaultRegistry.clear()
```

Another approach is to reset all sentries. Now, order of construction/reset is less of an issue. The disadvantage is
that resetting is not supported by all sentries. The concurrency limiting sentry and all metrics sentries ignore it.

```scala
SentrySupport.defaultRegistry.resetAllSentries()
```
