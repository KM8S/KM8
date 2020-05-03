package io.kafkamate
package kafka

import java.util.UUID

import api.ApiProvider._
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
import zio.stream.ZStream
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

    private [consumer] val kafkaConsumerLayer: URLayer[Clock with Blocking with ConfigProvider, KafkaConsumerProvider] =
      ZLayer.fromServices[
        Clock.Service,
        Blocking.Service,
        ConfigProvider.Service,
        KafkaConsumerProvider.Service](createService)

    val liveLayer: ULayer[KafkaConsumerProvider] =
      Clock.live ++ Blocking.live ++ ConfigProvider.liveLayer >>> kafkaConsumerLayer

    private def createService(clock: Clock.Service,
                              blocking: Blocking.Service,
                              configService: ConfigProvider.Service): Service =
      new Service with LoggingSupport {
        private val clockLayer: ULayer[Clock] = ZLayer.succeed(clock)
        private val blockingLayer: ULayer[Blocking] = ZLayer.succeed(blocking)
        private val timeout: Duration = 1000.millis

        private def consumerSettings: Task[ConsumerSettings] =
          configService.config.map { c =>
            ConsumerSettings(c.kafkaHosts)
              .withGroupId(UUID.randomUUID().toString)
              .withClientId("kafkamate")
              .withOffsetRetrieval(OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest))
              .withCloseTimeout(30.seconds)
          }

        private def consumerLayer(consumerSettings: ConsumerSettings): ULayer[Clock with Blocking with Consumer] =
          clockLayer ++ blockingLayer ++ ((clockLayer ++ blockingLayer) >>> Consumer.make(consumerSettings).orDie)

        def consumeAll(topic: String): Task[List[(String, String)]] = {
          val consumer: RIO[Clock with Blocking with Consumer, List[(String, String)]] =
            for {
              _ <- Consumer.subscribe(Subscription.Topics(Set(topic)))
              endOffsets <- Consumer.assignment.repeat(Schedule.doUntil(_.nonEmpty)).flatMap(Consumer.endOffsets(_, timeout))
              _ <- logger.infoIO( s"End offsets: $endOffsets")
              records <- Consumer
                .plainStream(Deserializer.string, Deserializer.string)
                .flattenChunks
                .takeUntil(cr => untilExists(endOffsets, cr))
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
            r <- consumer.provideLayer(consumerLayer(cs))
          } yield r
        }

        private def untilExists(endOffsets: Map[TopicPartition, Long],
                                cr: CommittableRecord[String, String]): Boolean =
          endOffsets.exists(o => o._1 == cr.offset.topicPartition && o._2 == 1 + cr.offset.offset)

        override def consumeStream(topic: String): Task[Stream[Task, Message]] =
          ok1(topic)

        private def ok1(topic: String): Task[Stream[Task, Message]] =
          ZIO {
            //import scala.concurrent.duration.{Duration, SECONDS}
            (for {
              //switch <- Stream.eval(Deferred[Task, Either[Throwable, Unit]])
              //switcher = Stream.eval(switch.complete(Right())).delayBy(Duration(20L, SECONDS))
              q <- Stream.bracket(FSQueue.noneTerminated[Task, Message])(terminateQueue)
              cs <- Stream.eval(consumerSettings)
              _ <- Stream.bracket[Task, Fiber.Runtime[Throwable, Unit]](
                Consumer
                  .consumeWith(cs, Subscription.Topics(Set(topic)), Serde.string, Serde.string)(enqueue(q))
                  .fork
                  .provideLayer(clockLayer ++ blockingLayer)
              )(_.interrupt *> logger.debugIO("Consumer fiber interrupted!"))
              //s = Stream.eval[Task, Unit](terminateQueue(q)).delayBy(Duration(15L, SECONDS))
              r <- q.dequeue.onFinalize(logger.debugIO("Stream terminated!"))//.concurrently(s)
            } yield r)
              .take(100)
          }

        private def enqueue(q: NoneTerminatedQueue[Task, Message])(k: String, v: String): UIO[Unit] =
          q.enqueue1(Some(Message(k, v))).foldM(
            e => logger.errorIO(s"Error pushing message: ${e.getMessage}"),
            _ => logger.debugIO(s"Pushed: ($k, $v)")
          )

        private def terminateQueue(q: NoneTerminatedQueue[Task, Message]): UIO[Unit] =
          q.enqueue1(None).foldM(
            e => logger.errorIO(s"Error terminating queue: ${e.getMessage}"),
            _ => logger.debugIO(s"FSQueue terminated!")
          )

        private def ok2(topic: String): Task[Stream[Task, Message]] =
          consumerSettings
            .flatMap(cs =>
              Consumer
                .subscribeAnd(Subscription.Topics(Set(topic)))
                .plainStream[Any, String, String](Deserializer.string, Deserializer.string)
                .flattenChunks
                .tap(cr => logger.debugIO(s"Msg: ${cr.record.key}"))
                .map(v => Message(v.record.key, v.record.value))
                .provideLayer(consumerLayer(cs))
                .unsafeRunToFStream
                .map(_.onFinalize(logger.debugIO("Stream terminated!")))
            )

        implicit private class ZStreamToFStream[A](private val zStream: ZStream[Any, Throwable, A]) {
          import zio.interop.reactivestreams._
          import fs2.interop.reactivestreams.fromPublisher
          def unsafeRunToFStream: Task[Stream[Task, A]] =
            ZIO.runtime[Any].map { implicit r =>
              r.unsafeRun(zStream.toPublisher >>= (p => Task(fromPublisher[Task, A](p))))
            }
        }

      }

  }

}