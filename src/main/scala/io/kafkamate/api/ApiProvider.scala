package io.kafkamate
package api

import com.twitter.finagle.{Service => FinchService}
import com.twitter.finagle.http.{Request, Response}
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

  trait Service extends Endpoint.Module[Task]  {
    def api: FinchService[Request, Response]
  }

  trait LiveApi extends Service {

    implicit val runtime: Runtime[ZEnv]

    case class Message(hello: String)

    private def healthcheck: Endpoint[Task, String] = get(pathEmpty) {
      Ok("OK")
    }

    private def helloWorld: Endpoint[Task, Message] = get("hello") {
      Ok(Message("World"))
    }

    private def hello: Endpoint[Task, Message] = get("hello" :: path[String]) { s: String =>
      Ok(Message(s))
    }

    private def bind: Endpoint[Task, Message :+: Message :+: CNil] = helloWorld :+: hello

    def api: FinchService[Request, Response] = Bootstrap
      .serve[Text.Plain](healthcheck)
      .serve[Application.Json](bind)
      .toService

  }
}
