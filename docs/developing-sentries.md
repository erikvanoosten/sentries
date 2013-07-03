# Developing sentries
## Guidelines

* Please use GitHub tracker, or better yet, do pull requests.
* Performance and simplicity is obtained by composing functionality from tiny building blocks.
* If it can't be made friendly to use, then leave it out.
* Code to make the library friendly to use is not part of the core.
* Readability of code triumphs all other considerations.
* If it doesn't perform, make it optional (this basically means that almost every feature is optional).
* Dependencies are avoided. Right now its: JVM 6+, Scala 2.9 and Akka 2.0.5 or Scala 2.10, Metrics-core 2.2.0.
* Follow effective scala guidelines from Twitter.
* Two space indents, use full imports, do not auto-format.
* API might break between major versions and before 1.0.0 is reached.
