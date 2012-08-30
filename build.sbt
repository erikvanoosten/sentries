name := "sentries"

organization := "nl.grons"

version := "0.1.2-SNAPSHOT"

scalaVersion := "2.9.2"

crossScalaVersions := Seq("2.9.1", "2.9.1-1", "2.9.2")
// crossScalaVersions := Seq("2.9.1", "2.9.1-1", "2.9.2", "2.10.0-M7")

resolvers ++= Seq(
  "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies <++= (scalaVersion) { v: String =>
  if (v.startsWith("2.10"))     Seq("com.yammer.metrics" % "metrics-core" % "2.1.2",
                                    "org.specs2" %% "specs2" % "1.11" % "test")
  else if (v.startsWith("2.9")) Seq("com.yammer.metrics" % "metrics-core" % "2.1.2",
                                    "com.typesafe.akka" % "akka-actor" % "2.0.3",
                                    "org.specs2" %% "specs2" % "1.11" % "test")
  else Seq()
}

javacOptions ++= Seq("-Xmx512m", "-Xms128m", "-Xss10m")

javaOptions += "-Xmx512m"

scalacOptions ++= Seq("-deprecation", "-unchecked")

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
