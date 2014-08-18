/*
 * Sentries
 * Copyright (c) 2012-2014 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the BSD 2-Clause License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

package nl.grons.sentries.cross

import java.util.concurrent.ExecutorService
import scala.collection.JavaConverters._

/**
 * Defines type aliases and helpers for abstracting differences between scala versions.
 * This version is for Scala 2.9.x and uses the Akka library for concurrency.
 */
object Concurrent {
  type ExecutionContext = akka.dispatch.ExecutionContext
  val ExecutionContext = akka.dispatch.ExecutionContext
  val Await = akka.dispatch.Await
  type Future[+A] = akka.dispatch.Future[A]
  val Future = akka.dispatch.Future
  type Duration = akka.util.Duration
  val Duration = akka.util.Duration
  type CMap[A, B] = scala.collection.mutable.ConcurrentMap[A, B]

  def defaultConcurrentMap[A,B](): CMap[A,B] = new java.util.concurrent.ConcurrentHashMap[A, B](1024).asScala

  def nonLoggingExecutionContext(executor: ExecutorService): akka.dispatch.ExecutionContextExecutorService =
    new NonLoggingWrappedExecutorService(executor)
}

class NonLoggingWrappedExecutorService(val executor: ExecutorService) extends
  akka.dispatch.ExecutorServiceDelegate with akka.dispatch.ExecutionContextExecutorService {
  override def reportFailure(t: Throwable) {}
}
