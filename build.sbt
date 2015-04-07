//
// Sentries
// Copyright (c) 2012-2015 Erik van Oosten All rights reserved.
//
// The primary distribution site is https://github.com/erikvanoosten/sentries
//
// This software is released under the terms of the BSD 2-Clause License.
// There is NO WARRANTY. See the file LICENSE for the full text.
//

name := "sentries"

organization := "nl.grons"

version := "0.8.0"

crossVersion := CrossVersion.binary

description <<= scalaVersion { sv =>
  "sentries for Scala " + sbt.cross.CrossVersionUtil.binaryScalaVersion(sv)
}

scalaVersion := "2.11.0"

crossScalaVersions := Seq("2.10.4", "2.11.4")

resolvers ++= Seq(
  "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies <++= (scalaVersion) { sv: String =>
  Seq(
    "com.yammer.metrics" % "metrics-core" % "2.2.0",
    "org.slf4j" % "slf4j-api" % "1.7.5"
  ) ++ (
    if (sv.startsWith("2.11"))      Seq("org.specs2" %% "specs2" % "2.3.11" % "test")
    else if (sv.startsWith("2.10")) Seq("org.specs2" %% "specs2" % "1.13" % "test")
    else sys.error("Not supported scala version: " + sv)
  )
}

scalacOptions ++= Seq("-deprecation", "-unchecked")

// Give tests lots of memory so that the time sensitive tests are not bitten by garbage collection.
// Prevent icons in the dock on Mountain Lion with the headless option.
javaOptions += "-Xms256m -Xmx512m -Djava.awt.headless=true"

// Running all tests in parallel gives too much contention.
testOptions in Test += Tests.Argument("threadsNb", "2")

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

credentials += Credentials(Path.userHome / ".sbt" / "sonatype.credentials")

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php"))

pomExtra := (
  <url>https://github.com/erikvanoosten/sentries</url>
  <scm>
    <url>git@github.com:erikvanoosten/sentries.git</url>
    <connection>scm:git:git@github.com:erikvanoosten/sentries.git</connection>
  </scm>
  <developers>
    <developer>
      <name>Erik van Oosten</name>
      <url>http://day-to-day-stuff.blogspot.com/</url>
    </developer>
  </developers>
)
