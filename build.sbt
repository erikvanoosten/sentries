name := "sentries"

version := "0.1"

scalaVersion := "2.9.1"

libraryDependencies ++= Seq(
    "com.yammer.metrics" %% "metrics-scala" % "2.1.2",
    "com.typesafe.akka" % "akka-actor" % "2.0.2",
    // Tests
    "org.specs2" %% "specs2" % "1.11" % "test"
    //
    // with Scala 2.8.x (specs2 1.5 is the latest version for scala 2.8.x)
    // "org.specs2" %% "specs2" % "1.5" % "test",
    // "org.specs2" %% "specs2-scalaz-core" % "5.1-SNAPSHOT" % "test"
  )

resolvers ++= Seq(
    "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
    "snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
    "releases"  at "http://oss.sonatype.org/content/repositories/releases",
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
  )
