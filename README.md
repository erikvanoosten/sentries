Sentries
===============

Sentries is an out-of-your way Scala library that will handle all the fault-handling
around calling resources like databases and remote services.

Sentries provides known techniques such as the Circuit Breaker, rate limiting,
load balancing and retries. You select what you need by composing several sentries
in a new sentry.
By combining this with time measurements and JMX control, Sentries is the ideal wrapper
for invoking databases, remote services, etc.

Example usage:

class Service extends SentrySupport {

val sentry = sentry("") 

TODO: small example
