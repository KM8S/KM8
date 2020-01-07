package io.kafkamate
package api

import com.twitter.finagle.{Service => FinchService}
import com.twitter.finagle.http.{Request, Response}
import com.github.mlangc.slf4zio.api._
import kafka.consumer.KafkaConsumerProvider
import kafka.producer.KafkaProducerProvider
import fs2.Stream
import io.finch._
import io.finch.fs2._
import io.finch.circe._
import io.circe.generic.auto._
import shapeless.{:+:, CNil}
import zio._
import zio.stream.ZSink
import zio.interop.catz._

trait ApiProvider {
  def apiProvider: ApiProvider.Service
}

object ApiProvider {

  trait Env extends KafkaConsumerProvider.Env with KafkaConsumerProvider with KafkaProducerProvider.Env with KafkaProducerProvider

  case class Message(key:String, value: String)

  type KIO[A] = RIO[Env, A]

  trait Service extends Endpoint.Module[KIO]  {
    def api: FinchService[Request, Response]
  }

  trait LiveApi extends Service with LoggingSupport {

    implicit val runtime: Runtime[Env]

    private def healthcheck: Endpoint[KIO, String] = get(pathEmpty) {
      Ok("OK"): Output[String]
    }

    private def produceEvent: Endpoint[KIO, String] = get("produce") {
      val res: KIO[Output[String]] =
        ZIO.accessM[Env](_.kafkaProducer.produce("test", "bla", "foo")).as {
          Ok("posted")
        }
      res
    }

    private def foreverStream: Endpoint[KIO, Stream[KIO, Message]] = get("forever") {
      import scala.concurrent.duration._
      def res2: KIO[Output[Stream[KIO, Message]]] =
        ZIO(Ok(
          Stream
            .bracket(ZIO((java.util.UUID.randomUUID().toString, "test")): KIO[(String, String)])(_ => logger.debugIO("for ever release "))
            .delayBy(1.second)
            .repeat
            .map(v => Message(v._1, v._2))
        ))
      res2.catchAll {
        e: Throwable => logger.debugIO("catch err - " + e.getMessage) *> ZIO(Ok(Stream.empty: Stream[KIO, Message]))
      }: KIO[Output[Stream[KIO, Message]]]
    }

    private def consumeAll: Endpoint[KIO, List[Message]] = get("consume" :: "all" :: path[String]) { topic: String =>
      val res: KIO[Output[List[Message]]] =
        ZIO.accessM[Env](_.kafkaConsumer.consumeAll(topic)).map { lst =>
          Ok(lst.map(v => Message(v._1, v._2)))
        }
      res
    }

    private def consumeStream: Endpoint[KIO, Stream[KIO, Message]] = get("consume" :: "stream" :: path[String]) { topic: String =>
      val res: KIO[Output[Stream[KIO, Message]]] =
        ZIO.accessM[Env](_.kafkaConsumer.consumeStream(topic))
          .map(Ok)
          .catchAll { e: Throwable =>
            logger.debugIO("catch err - " + e.getMessage) *> ZIO(BadRequest(new Exception("big failure")))
          }
      res
    }

    private def bind: Endpoint[KIO, String :+: List[Message] :+: CNil] = produceEvent :+: consumeAll

    def api: FinchService[Request, Response] = Bootstrap
      .serve[Text.Plain](healthcheck)
      .serve[Application.Json](bind)
      .serve[Application.Json](foreverStream)
      .serve[Application.Json](consumeStream)
      .toService

  }
}
