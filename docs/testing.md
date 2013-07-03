# Testing applications that use sentries

As sentries are effectively singletons (only one instance allowed per sentries registry), you may have problems testing sentries that keep state (such as the circuit breaker). To make sure that fresh sentries are created for every test you have 2 options.

The first option is to reset all sentries before the test starts (for example from a 'before' method):

```scala
SentrySupport.defaultRegistry.resetAllSentries()
```

The advantage is that there is not a lot of code however, it will fail when you run tests concurrently. Another disadvantage is that resetting is not supported by all sentries. The concurrency limiting sentry and all metrics sentries ignore it.

To run tests concurrently you will need to override the sentries registry for each instance of the service under test:

```scala
  val serviceUnderTest = new DoItAllService {
    override def sentryRegistry = new SentriesRegistry
  }
```
