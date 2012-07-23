# Sentries

Sentries is an out-of-your way Scala library that will handle all the fault-handling
around calling resources like databases and remote services.

Sentries provides known techniques such as the Circuit Breaker, rate limiting,
load balancing and retries. You select what you need by composing several sentries
in a new sentry.
By combining this with time measurements and JMX control, Sentries is the ideal wrapper
for invoking databases, remote services, etc.

Example usage:
```scala
class DoItAllService extends SentrySupport {

  val dbSentry = sentry("mysql:localhost:3366") withFailLimit(failLimit = 5, retryDelayMillis = 500)
  val twitterApiSentry = sentry("twitter") withFailLimit(failLimit = 5, retryDelayMillis = 500) withConcurrencyLimit(3)

  def loadTweetFromDb(id: Long): Tweet = dbSentry {
    database.load(id)
  }

  def getFromTwitter(id: Long): Tweet = twitterApiSentry {
    twitterApi.load(id)
  }
}
```

See [SentryExampleApp](/erikvanoosten/sentries/blob/master/src/main/scala/nl/grons/sentries/examples/SentryExampleApp.scala) for a more elaborate example.

## Developer guidelines

* Performance and simplicity is obtained by composing functionality from tiny building blocks.
* If it can't be made friendly to use, then leave it out.
* Code to make the library friendly to use is not part of the core.
* Readability of code triumphs all other considerations.
* If it doesn't perform, make it optional (this basically means that almost every feature is optional).
* Dependencies are avoided. Right now its: JVM 6, Scala 2.9 and Akka 2.0.2 (later: Scala 2.10), Metrics-core 2.1.2.
* Follow effective scala guidelines from Twitter
* Two space indents, use full imports, do not auto-format
* API might break between major versions and before 1.0.0 is reached.
