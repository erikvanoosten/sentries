//
// Sentries
// Copyright (c) 2012-2013 Erik van Oosten All rights reserved.
//
// The primary distribution site is https://github.com/erikvanoosten/sentries
//
// This software is released under the terms of the BSD 2-Clause License.
// There is NO WARRANTY. See the file LICENSE for the full text.
//

name := "sentries"

organization := "nl.grons"

version := "0.7.0"

scalaVersion := "2.10.0"

crossScalaVersions := Seq("2.9.1", "2.9.1-1", "2.9.2", "2.9.3", "2.10.0")

crossVersion := CrossVersion.binary

// The following prepends src/main/scala_2.9 or src/main/scala_2.10 to the compile path.
unmanagedSourceDirectories in Compile <<= (unmanagedSourceDirectories in Compile, sourceDirectory in Compile, scalaVersion) { (sds: Seq[java.io.File], sd: java.io.File, v: String) =>
  val mainVersion = v.split("""\.""").take(2).mkString(".")
  val extra = new java.io.File(sd, "scala_" + mainVersion)
  (if (extra.exists) Seq(extra) else Seq()) ++ sds
}

resolvers ++= Seq(
  "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies <++= (scalaVersion) { v: String =>
  Seq("com.yammer.metrics" % "metrics-core" % "2.2.0", "org.slf4j" % "slf4j-api" % "1.7.5") ++ (
      if (v.startsWith("2.10"))     Seq("org.specs2" %% "specs2" % "1.13" % "test")
      else if (v == "2.9.3")        Seq("com.typesafe.akka" % "akka-actor" % "2.0.5",
                                        "org.specs2" % "specs2_2.9.2" % "1.12.3" % "test")
      else if (v.startsWith("2.9")) Seq("com.typesafe.akka" % "akka-actor" % "2.0.5",
                                        "org.specs2" %% "specs2" % "1.12.3" % "test")
      else sys.error("Not supported scala version: " + v))
}

scalacOptions ++= Seq("-deprecation", "-unchecked")

// Give tests lots of memory so that the time sensitive tests are not bitten by garbage collection.
javaOptions += "-Xms256m -Xmx512m"

// Running all tests in parallel gives too much contention.
testOptions in Test += Tests.Argument("threadsNb", "2")

publishTo <<= version { v: String =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
  else                             Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

credentials += Credentials(Path.userHome / ".sbt" / "sonatype.credentials")

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
      <url>http://day-to-day-stuff.blogspot.com/</url>
    </developer>
  </developers>
)
