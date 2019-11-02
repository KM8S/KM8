package io.kafkamate
package api

import com.twitter.finagle.{Service => FinchService}
import com.twitter.finagle.http.{Request, Response}
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

  trait LiveApi extends Service {

    implicit val runtime: Runtime[Env]

    case class Message(key:String, value: String)

    private def healthcheck: Endpoint[KIO, String] = get(pathEmpty) {
      ZIO(Ok("OK")): KIO[Output[String]]
    }

    private def helloWorld: Endpoint[KIO, Message] = get("hello") {
      Ok(Message("Hello", "World"))
    }

    private def kafka: Endpoint[KIO, List[Message]] = get("kafka") {
      val res: KIO[Output[List[Message]]] =
        ZIO.accessM[Env](_.kafkaConsumer.consume).map { lst =>
          Ok(lst.map(v => Message(v._1, v._2)))
        }
      res
    }

    private def hello: Endpoint[KIO, Message] = get("hello" :: path[String]) { s: String =>
      Ok(Message("Hello", s))
    }

    private def bind: Endpoint[KIO, Message :+: Message :+: CNil] = helloWorld :+: hello

    def api: FinchService[Request, Response] = Bootstrap
      .serve[Text.Plain](healthcheck)
      .serve[Application.Json](bind)
      .serve[Application.Json](kafka)
      .toService

  }
}
