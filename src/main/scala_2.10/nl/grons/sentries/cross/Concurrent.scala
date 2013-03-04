/*
 * Sentries
 * Copyright (c) 2012-2013 Erik van Oosten All rights reserved.
 *
 * The primary distribution site is https://github.com/erikvanoosten/sentries
 *
 * This software is released under the terms of the BSD 2-Clause License.
 * There is NO WARRANTY. See the file LICENSE for the full text.
 */

package nl.grons.sentries.cross

import java.util.concurrent.Executor

/**
 * Defines type aliases and helpers for abstracting differences between scala versions.
 * This version is for Scala 2.10.x and uses the scala library for concurrency.
 */
object Concurrent {
  type ExecutionContext = scala.concurrent.ExecutionContext
  val ExecutionContext = scala.concurrent.ExecutionContext
  val Await = scala.concurrent.Await
  type Future[+A] = scala.concurrent.Future[A]
  val Future = scala.concurrent.Future
  type Duration = scala.concurrent.duration.Duration
  val Duration = scala.concurrent.duration.Duration
  type CMap[A, B] = scala.collection.concurrent.Map[A, B]

  def defaultConcurrentMap[A,B](): CMap[A,B] = scala.collection.concurrent.TrieMap.empty

  def nonLoggingExecutionContext(executor: Executor): scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.fromExecutor(executor, reporter = (_: Throwable) => ())
}
