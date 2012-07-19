/*
 * Copyright (c) 2012 Erik van Oosten
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import nl.grons.sentries.SentrySupport

//
//import org.specs.SpecificationWithJUnit
//import org.slf4j.LoggerFactory
//import circuitbreaker.{Sentries, CircuitBreakerRetryException, CircuitBreakerBrokenException}
//
//
///**
// * class that uses trait UsingCircuitBreaker
// */
class SimpleCircuitBreaker extends SentrySupport {
  //
  //  val circuitBreaker = sentries.failLimit(CircuitBreakerConfiguration(100, 10))
  //
  //  /**
  //   * method that uses configured CircuitBreaker with name "test"
  //   * method works fine (means no exception)
  //   */
  //  def myMethod() {
  //    circuitBreaker {
  //    }
  //  }
  //
  //  /**
  //   * method that uses configured CircuitBreaker with name "test"
  //   * method throws a IllegalArgumentException
  //   * means: Calls are failing
  //   */
  //  def my2Method() {
  //    circuitBreaker {
  //      throw new java.lang.IllegalArgumentException
  //    }
  //  }
  //
  //}
  //
  //
  ///**
  // * the TEST
  // */
  //
  //class TestCircuitBreaker extends SpecificationWithJUnit {
  //  "A CircuitBreaker" should {
  //    setSequential()
  //    shareVariables()
  //
  //    "remain closed (no exceptions are thrown)" in {
  //      val testClass = new SimpleCircuitBreaker()
  //      for (i <- 1 to 20) {
  //        testClass.myMethod() must not(throwA[CircuitBreakerOpenException])
  //      }
  //    }
  //
  //    "be changing states correctly" in {
  //      val testClass = new SimpleCircuitBreaker()
  //
  //      /**
  //       * 10 failures throwing IllegalArgumentException
  //       */
  //      for (i <- 1 to 10) {
  //        testClass.my2Method() must throwA[java.lang.IllegalArgumentException]
  //      }
  //
  //
  //      /**
  //       * calls are failing fast (invoke is not called) for
  //       * 100ms (this is configured for CircuitBreaker "test")
  //       */
  //      for (i <- 1 to 10) {
  //        testClass.my2Method() must throwA[CircuitBreakerOpenException]
  //      }
  //
  //      /**
  //       * sleep for more than 100ms
  //       */
  //      Thread.sleep(200)
  //
  //
  //      /**
  //       * CircuitBreaker should be half open then (after 100ms timeout)
  //       */
  //      testClass.my2Method() must throwA[CircuitBreakerHalfOpenException]
  //
  //
  //      /**
  //       * still failing? then CircuitBreaker should be open again
  //       */
  //      for (i <- 1 to 10) {
  //        testClass.my2Method() must throwA[CircuitBreakerOpenException]
  //      }
  //    }
  //
  //  }
}