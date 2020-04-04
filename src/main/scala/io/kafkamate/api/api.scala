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
import zio.clock.Clock
import zio.stream.ZSink
import zio.interop.catz._

package object api {
  type ApiProvider = Has[ApiProvider.Service]
  type RuntimeProvider = Has[Runtime[ZEnv]]

  object ApiProvider {

    case class Message(key: String, value: String)

    trait Service extends Endpoint.Module[Task]  {
      def api: FinchService[Request, Response]
    }

    val apiLayer: URLayer[RuntimeProvider with KafkaConsumerProvider with KafkaProducerProvider, ApiProvider] =
      ZLayer.fromServices[Runtime[ZEnv], KafkaConsumerProvider.Service, KafkaProducerProvider.Service, ApiProvider.Service] { (r, kafkaConsumer, kafkaProducer) =>
        new Service with LoggingSupport {

          implicit val runtime: Runtime[ZEnv] = r

          private def healthcheck: Endpoint[Task, String] = get(pathEmpty) {
            Ok("OK"): Output[String]
          }

          private def produceEvent: Endpoint[Task, String] = get("produce") {
            val res: Task[Output[String]] =
              kafkaProducer.produce("test", "bla", "foo").as {
                Ok("posted")
              }
            res
          }

          private def foreverStream: Endpoint[Task, Stream[Task, Message]] = get("forever") {
            import scala.concurrent.duration._
            implicit val timer: cats.effect.Timer[Task] = new cats.effect.Timer[ZIO[Any, Throwable, *]] {
              override final def clock: cats.effect.Clock[ZIO[Any, Throwable, *]] = new cats.effect.Clock[ZIO[Any, Throwable, *]] {
                override final def monotonic(unit: TimeUnit): ZIO[Any, Throwable, Long] =
                  zio.clock.nanoTime.map(unit.convert(_, NANOSECONDS)).provideLayer(Clock.live)
                override final def realTime(unit: TimeUnit): ZIO[Any, Throwable, Long] =
                  zio.clock.currentTime(unit).provideLayer(Clock.live)
              }
              override final def sleep(duration: FiniteDuration): ZIO[Any, Throwable, Unit] =
                zio.clock.sleep(zio.duration.Duration.fromNanos(duration.toNanos)).provideLayer(Clock.live)
            }
            val res: Task[Output[Stream[Task, Message]]] =
              ZIO(Ok(
                Stream
                  .bracket[Task, Message](ZIO(Message(java.util.UUID.randomUUID().toString, "test")))(_ => logger.debugIO("for ever release "))
                  .delayBy(1.second)
                  .repeat
                  .onFinalize(logger.debugIO("fs2 forever interruption, working"))
              )).onInterrupt(logger.debugIO("zio forever interruption, not working"))
            res
          }

          private def consumeAll: Endpoint[Task, List[Message]] = get("consume" :: "all" :: path[String]) { topic: String =>
            val res: Task[Output[List[Message]]] =
              kafkaConsumer.consumeAll(topic).map { lst =>
                Ok(lst.map(v => Message(v._1, v._2)))
              }.onInterrupt(logger.debugIO("consumer all interruption, working"))
            res
          }

          private def consumeStream: Endpoint[Task, Stream[Task, Message]] = get("consume" :: "stream" :: path[String]) { topic: String =>
            val res: Task[Output[Stream[Task, Message]]] =
              kafkaConsumer.consumeStream(topic).map(Ok).onInterrupt(logger.debugIO("consumeStream interruption, not working"))
            res
          }

          private def bind: Endpoint[Task, String :+: List[Message] :+: CNil] = produceEvent :+: consumeAll

          def api: FinchService[Request, Response] = Bootstrap
            .serve[Text.Plain](healthcheck)
            .serve[Application.Json](bind)
            .serve[Application.Json](foreverStream)
            .serve[Application.Json](consumeStream)
            .toService

        }
      }

    val liveLayer: URLayer[RuntimeProvider, ApiProvider] =
      (ZLayer.requires[RuntimeProvider] ++ KafkaConsumerProvider.liveLayer ++ KafkaProducerProvider.liveLayer) >>> apiLayer
  }
}
