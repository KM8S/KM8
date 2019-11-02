package io.kafkamate
package api

import com.twitter.finagle.{Service => FinchService}
import com.twitter.finagle.http.{Request, Response}
import com.github.mlangc.slf4zio.api._
import kafka.consumer.KafkaConsumerProvider
import io.finch._
import io.finch.circe._
import io.circe.generic.auto._
import shapeless.{:+:, CNil}
import zio._
import zio.interop.catz._

trait ApiProvider {
  def apiProvider: ApiProvider.Service
}

object ApiProvider {

  trait Env extends KafkaConsumerProvider.Env with KafkaConsumerProvider

  type KIO[A] = RIO[Env, A]

  trait Service extends Endpoint.Module[KIO]  {
    def api: FinchService[Request, Response]
  }

  trait LiveApi extends Service with LoggingSupport {

    implicit val runtime: Runtime[Env]

    case class Message(key:String, value: String)

    private def healthcheck: Endpoint[KIO, String] = get(pathEmpty) {
      ZIO(Ok("OK")): KIO[Output[String]]
    }

    private def helloWorld: Endpoint[KIO, Message] = get("hello") {
      Ok(Message("Hello", "World"))
    }

    private def kafka: Endpoint[KIO, List[Message]] = get("kafka" :: path[String]) { topic: String =>
      val res: KIO[Output[List[Message]]] =
        ZIO.accessM[Env](_.kafkaConsumer.consume(topic)).map { lst =>
          Ok(lst.map(v => Message(v._1, v._2)))
        }
      res
    }

    private def bind: Endpoint[KIO, Message :+: List[Message] :+: CNil] = helloWorld :+: kafka

    def api: FinchService[Request, Response] = Bootstrap
      .serve[Text.Plain](healthcheck)
      .serve[Application.Json](bind)
      .toService

  }
}
