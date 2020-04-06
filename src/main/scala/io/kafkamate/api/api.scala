package io.kafkamate

import com.twitter.finagle.{Service => FinchService}
import com.twitter.finagle.http.{Request, Response}
import com.github.mlangc.slf4zio.api._
import kafka.consumer._
import kafka.producer._
import fs2.Stream
import io.finch._
import io.finch.fs2._
import io.finch.circe._
import io.circe.generic.auto._
import shapeless.{:+:, CNil}
import zio._
import zio.interop.catz._

package object api {
  type ApiProvider = Has[ApiProvider.Service]
  type RuntimeProvider = Has[Runtime[ZEnv]]

  object ApiProvider {

    case class Message(key: String, value: String)

    trait Service extends Endpoint.Module[Task]  {
      def api: FinchService[Request, Response]
    }

    private [api] val apiLayer: URLayer[RuntimeProvider with KafkaConsumerProvider with KafkaProducerProvider, ApiProvider] =
      ZLayer.fromServices[
        Runtime[ZEnv],
        KafkaConsumerProvider.Service,
        KafkaProducerProvider.Service,
        ApiProvider.Service](createService)

    val liveLayer: URLayer[RuntimeProvider, ApiProvider] =
      ZLayer.requires[RuntimeProvider] ++ KafkaConsumerProvider.liveLayer ++ KafkaProducerProvider.liveLayer >>> apiLayer

    private def createService(r: Runtime[ZEnv],
                              consumerService: KafkaConsumerProvider.Service,
                              producerService: KafkaProducerProvider.Service): Service =
      new Service with LoggingSupport {
        implicit val runtime: Runtime[ZEnv] = r

        def api: FinchService[Request, Response] = Bootstrap
          .serve[Application.Json](bind)
          .serve[Application.Json](consumeStream)
          .toService

        private def bind: Endpoint[Task, String :+: List[Message] :+: CNil] = produceMessage :+: consumeAll

        private def produceMessage: Endpoint[Task, String] = get("produce") {
          val output: Task[Output[String]] =
            producerService
              .produce("test", "bla", "foo")
              .as(Ok("Successfully produced!"))
          output
        }

        private def consumeAll: Endpoint[Task, List[Message]] = get("consume" :: "all" :: path[String]) { topic: String =>
          val output: Task[Output[List[Message]]] =
            consumerService
              .consumeAll(topic)
              .map { lst => Ok(lst.map(v => Message(v._1, v._2))) }
              .onInterrupt(logger.debugIO("consumer all interruption, working"))
          output
        }

        private def consumeStream: Endpoint[Task, Stream[Task, Message]] = get("consume" :: "stream" :: path[String]) { topic: String =>
          val output: Task[Output[Stream[Task, Message]]] =
            consumerService
              .consumeStream(topic)
              .map(Ok)
              .onInterrupt(logger.debugIO("consumeStream interruption, not working"))
          output
        }
      }
  }
}
