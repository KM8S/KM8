package io.kafkamate
package kafka

import java.util.UUID

import api.ApiProvider
import ApiProvider._
import config.ConfigProvider
import com.github.mlangc.slf4zio.api._
import cats.effect.concurrent.Deferred
import fs2.Stream
import fs2.concurrent.{NoneTerminatedQueue, Queue => FSQueue}
import org.apache.kafka.common.TopicPartition
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.stream.{Take, ZSink, ZStream}
import zio.interop.catz._
import zio.kafka.consumer._
import zio.kafka.consumer.Consumer._
import zio.kafka.serde.{Deserializer, Serde}

package object consumer {
  type RuntimeProvider = Has[Runtime[ZEnv]]
  type KafkaConsumerProvider = Has[KafkaConsumerProvider.Service]

  object KafkaConsumerProvider {

    trait Service {
      def consumeAll(topic: String): Task[List[(String, String)]]
      def consumeStream(topic: String): Task[Stream[Task, Message]]
    }

    val kafkaConsumerLayer: URLayer[RuntimeProvider with Clock with Blocking with ConfigProvider, KafkaConsumerProvider] =
      ZLayer.fromServices[Runtime[ZEnv], Clock.Service, Blocking.Service, ConfigProvider.Service, KafkaConsumerProvider.Service] { (runtime, clock, blocking, configProvider) =>
        new Service with LoggingSupport {
          val clockLayer: ULayer[Clock] = ZLayer.succeed(clock)
          val blockingLayer: ULayer[Blocking] = ZLayer.succeed(blocking)
          val runtimeLayer: ULayer[RuntimeProvider] = ZLayer.succeed(runtime)
          private val timeout: Duration = 1000.millis

          def consumerSettings: Task[ConsumerSettings] =
            configProvider.config.map { c =>
              ConsumerSettings(c.kafkaHosts)
                .withGroupId(UUID.randomUUID().toString)
                .withClientId("kafkamate")
                .withOffsetRetrieval(OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest))
                .withCloseTimeout(30.seconds)
            }

          def consumeAll(topic: String): Task[List[(String, String)]] = {
            def layer(consumerSettings: ConsumerSettings) =
              clockLayer ++ blockingLayer ++ ((clockLayer ++ blockingLayer) >>> Consumer.make(consumerSettings, Deserializer.string, Deserializer.string).orDie)
            val zio: RIO[Clock with Blocking with Consumer[Any, String, String], List[(String, String)]] =
              for {
                _ <- Consumer.subscribe[Any, String, String](Subscription.Topics(Set(topic)))
                endOffsets <- Consumer.assignment[Any, String, String].repeat(Schedule.doUntil(_.nonEmpty)).flatMap(Consumer.endOffsets[Any, String, String](_, timeout))
                _ <- logger.infoIO( s"End offsets: $endOffsets")
                stream = Consumer
                  .plainStream[Any, String, String]
                  .flattenChunks
                  .takeUntil(cr => untilExists(endOffsets, cr))
                records <- stream
                  .runCollect
                  .flatMap { recs =>
                    recs
                      .foldLeft(OffsetBatch.empty)((b, r) => b merge r.offset)
                      .commit
                      .as(recs.map(r => (r.record.key, r.record.value)))
                  }
              } yield records
            for {
              cs <- consumerSettings
              r <- zio.provideLayer(layer(cs))
            } yield r
          }

          private def untilExists(endOffsets: Map[TopicPartition, Long],
                                  cr: CommittableRecord[String, String]): Boolean =
            endOffsets.exists(o => o._1 == cr.offset.topicPartition && o._2 == 1 + cr.offset.offset)

          override def consumeStream(topic: String): Task[Stream[Task, Message]] =
            ok3(topic)

          private def ok1(topic: String): Task[Stream[Task, Message]] = {
            val layer = clockLayer ++ blockingLayer
            //import scala.concurrent.duration.{Duration, SECONDS}
            ZIO {
              (for {
                //switch <- Stream.eval(Deferred[Task, Either[Throwable, Unit]])
                //switcher = Stream.eval(switch.complete(Right())).delayBy(Duration(20L, SECONDS))
                q <- Stream.bracket(FSQueue.noneTerminated[Task, Message])(terminateQueue)
                cs <- Stream.eval(consumerSettings)
                _ <- Stream.bracket[Task, Fiber[Throwable, Unit]](
                  Consumer
                    .consumeWith[Any, Any, String, String](cs, Subscription.Topics(Set(topic)), Serde.string, Serde.string)(enqueue(q))
                    .fork
                    .provideLayer(layer)
                )(_.interrupt *> logger.debugIO("Consumer fiber interrupted!"))
                //s = Stream.eval[Task, Unit](terminateQueue(q)).delayBy(Duration(15L, SECONDS))
                r <- q.dequeue.onFinalize(logger.debugIO("Stream terminated!"))//.concurrently(s)
              } yield r).take(100)
            }
          }

          private def enqueue(q: NoneTerminatedQueue[Task, Message])(k: String, v: String): ZIO[Any, Nothing, Unit] =
            q.enqueue1(Some(Message(k, v))).foldM(
              e => logger.errorIO(s"Error pushing message: ${e.getMessage}"),
              _ => logger.debugIO(s"Pushed: ($k, $v)")
            )

          private def terminateQueue(q: NoneTerminatedQueue[Task, Message]): ZIO[Any, Nothing, Unit] =
            q.enqueue1(None).foldM(
              e => logger.errorIO(s"Error terminating queue: ${e.getMessage}"),
              _ => logger.debugIO(s"FSQueue terminated!")
            )

          /*private def ok3(topic: String): Task[Stream[Task, Message]] =
            ZStream
              .managed(Consumer.make[Any, String, String](consumerSettings, Deserializer.string, Deserializer.string))
              .flatMap {
                _.subscribeAnd(Subscription.Topics(Set(topic)))
                  .plainStream(Serde.string, Serde.string)
                  .flattenChunks
                  .tap(cr => logger.debugIO(s"Msg: ${cr.record.key}"))
                  .map(v => Message(v.record.key, v.record.value))
              }
              .unsafeRunToStream
              .map(_.onFinalize(logger.debugIO("Stream terminated!")))*/

          private def ok3(topic: String): Task[Stream[Task, Message]] = {
            def layer(consumerSettings: ConsumerSettings) =
              runtimeLayer ++ clockLayer ++ blockingLayer ++ ((clockLayer ++ blockingLayer) >>> Consumer.make(consumerSettings, Deserializer.string, Deserializer.string).orDie)
            val zio = Consumer
              .subscribeAnd[Any, String, String](Subscription.Topics(Set(topic)))
              .plainStream
              .flattenChunks
              .tap(cr => logger.debugIO(s"Msg: ${cr.record.key}"))
              .map(v => Message(v.record.key, v.record.value))
            consumerSettings
              .map(cs => zio.provideLayer(layer(cs)).unsafeRunToFStream(runtime).onFinalize(logger.debugIO("Stream terminated!")))
          }

          implicit private class ZStreamToFStream[A](private val stream: ZStream[Any, Throwable, A]) {
            import zio.interop.reactivestreams._
            import fs2.interop.reactivestreams.fromPublisher
            def unsafeRunToFStream(implicit r: Runtime[Any]): Stream[Task, A] = {
              r.unsafeRun(stream.toPublisher >>= (p => Task(fromPublisher[Task, A](p))))
            }
          }

        }
      }

    val liveLayer: URLayer[RuntimeProvider, KafkaConsumerProvider] =
      (ZLayer.requires[RuntimeProvider] ++ Clock.live ++ Blocking.live ++ ConfigProvider.live) >>> kafkaConsumerLayer

  }

}