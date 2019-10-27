package io.kafkamate
package util

package object implicits {

  import scala.language.implicitConversions

  implicit def twitterToScalaFuture[T](f: => com.twitter.util.Future[T]): scala.concurrent.Future[T] = {
    val promise = scala.concurrent.Promise[T]()
    f.respond(t => promise.complete(t.asScala))
    promise.future
  }

}
