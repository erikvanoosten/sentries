# Download

Sentries is build for several scala versions.

<table border="0" cellpadding="2" cellspacing="2">
  <tbody>
    <tr>
      <td rowspan="2" valign="top">Sentries<br>version</td>
      <td rowspan="2" valign="top">Metrics<br>version</td>
      <td colspan="3" valign="top">Scala version</td>
    </tr>
    <tr>
      <td valign="top">2.9.*</td>
      <td valign="top">2.10</td>
      <td valign="top">2.11</td>
    </tr>
    <tr>
      <td valign="top">0.7.1</td>
      <td valign="top">2.2.0</td>
      <td valign="top">✓</td>
      <td valign="top">✓</td>
      <td valign="top">✓</td>
    </tr>
    <tr>
      <td valign="top">0.8.0</td>
      <td valign="top">2.2.0</td>
      <td valign="top"></td>
      <td valign="top">✓</td>
      <td valign="top">✓</td>
    </tr>
  </tbody>
</table>

Scala version `2.9.*` refers to the following versions: `2.9.1`, `2.9.1-1`, `2.9.2`, `2.9.3`. The 2.9.* releases require Akka 2.0.x and are build against Akka 2.0.5.


## SBT
```
libraryDependencies += "nl.grons" %% "sentries" % "0.8.0"
```

## Maven
```
<properties>
    <scala.version>2.11.0</scala.version>
    <scala.dep.version>2.11</scala.dep.version>
</properties>
<dependency>
    <groupId>nl.grons</groupId>
    <artifactId>sentries_${scala.dep.version}</artifactId>
    <version>0.8.0</version>
</dependency>
```

## Changes

### Version 0.8, November 2014

* _NOT_ binary backward compatible. This version _is_ source compatible with 0.7.x.
* Dropped scala 2.9 support.
* All durations typed as `FiniteDuration` instead of `Duration` (both from package `scala.concurrent.duration`).
* Added parameter `failedInvocationDurationThreshold` to `AdaptiveThroughputSentry` in order to not reduce throughput on fast failures.
  This features was introduced after a long discussion on a hack night of the [Amsterdam Scala group](http://www.meetup.com/amsterdam-scala/).
