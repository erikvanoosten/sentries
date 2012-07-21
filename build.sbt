name := "sentries"

organization := "nl.grons"

version := "0.1"

scalaVersion := "2.9.1"

libraryDependencies ++= Seq(
    "com.yammer.metrics" % "metrics-core" % "2.1.2",
    "com.typesafe.akka" % "akka-actor" % "2.0.2"
    // Tests
    // "org.specs2" %% "specs2" % "1.11" % "test"
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

publishTo <<= version { v: String =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
  else                             Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php"))

homepage := Some(url("http://sentries.grons.nl"))

pomExtra := (
  <scm>
    <url>git@github.com:erikvanoosten/sentries.git</url>
    <connection>scm:git:git@github.com:erikvanoosten/sentries.git</connection>
  </scm>
  <developers>
    <developer>
      <id>erikvanoosten</id>
      <name>Erik van Oosten</name>
      <url>http://www.grons.nl</url>
    </developer>
  </developers>
)