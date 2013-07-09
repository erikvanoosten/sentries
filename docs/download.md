# Download

Sentries is build for scala 2.9.1, 2.9.1-1, 2.9.2, 2.9.3 and 2.10. Akka is not needed for the 2.10 build.

## SBT
```
libraryDependencies += "nl.grons" %% "sentries" % "0.7.1"
```

## Maven
```
<properties>
    <scala.version>2.10.0</scala.version>
    <scala.dep.version>2.10</scala.dep.version>
</properties>
<dependency>
    <groupId>nl.grons</groupId>
    <artifactId>sentries_${scala.dep.version}</artifactId>
    <version>0.7.1</version>
</dependency>
```

Note: For scala versions before 2.10, you need to use the full scala version; e.g. `metrics-scala_2.9.1-1`.
