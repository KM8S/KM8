package io.kafkamate
package kafka
package consumer

import java.util.UUID

import api.ApiProvider
import ApiProvider._
import org.apache.kafka.clients.consumer.ConsumerConfig
import com.github.mlangc.slf4zio.api._
import cats.effect.concurrent.Deferred
import fs2.Stream
import fs2.concurrent.{Queue => FSQueue, NoneTerminatedQueue}
import org.apache.kafka.common.TopicPartition
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.kafka.client._
import zio.kafka.client.serde.Serde
import zio.stream.{Take, ZSink, ZStream}
import zio.interop.catz._

trait KafkaConsumerProvider {
  def kafkaConsumer: KafkaConsumerProvider.Service
}

object KafkaConsumerProvider {

  trait Env extends Clock with Blocking

  trait Service {
    def consumeAll(topic: String): RIO[Env, List[(String, String)]]
    def consumeStream(topic: String): KIO[Stream[KIO, Message]]
  }

  trait LiveConsumer extends KafkaConsumerProvider with LoggingSupport {

    private def consumerSettings = ConsumerSettings(
      bootstrapServers          = List(s"localhost:9092"),
      groupId                   = UUID.randomUUID().toString,
      clientId                  = "kafkamate",
      closeTimeout              = 30.seconds,
      extraDriverSettings       = Map(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "earliest"),
      pollInterval              = 250.millis,
      pollTimeout               = 50.millis,
      perPartitionChunkPrefetch = 2
    )

    private val timeout: Duration = 1000.millis

    def kafkaConsumer: Service = new Service {

      def consumeAll(topic: String): RIO[Env, List[(String, String)]] =
        Consumer.make(consumerSettings).use { c =>
          for {
            _ <- c.subscribe(Subscription.Topics(Set(topic)))
            endOffsets <- c.assignment.repeat(Schedule.doUntil(_.nonEmpty)).flatMap(c.endOffsets(_, timeout))
            _ <- logger.infoIO( s"End offsets: $endOffsets")
            stream = c
              .plainStream(Serde.string, Serde.string)
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
        }

      private def untilExists(endOffsets: Map[TopicPartition, Long],
                              cr: CommittableRecord[String, String]): Boolean =
        endOffsets.exists(o => o._1 == cr.offset.topicPartition && o._2 == 1 + cr.offset.offset)

      override def consumeStream(topic: String): KIO[Stream[KIO, Message]] =
        ok1(topic)

      private def ok1(topic: String): KIO[Stream[KIO, Message]] = {
        import scala.concurrent.duration.{Duration, SECONDS}
        ZIO {
          (for {
            switch <- Stream.eval(Deferred[KIO, Either[Throwable, Unit]])
            //switcher = Stream.eval(switch.complete(Right())).delayBy(Duration(20L, SECONDS))
            q <- Stream.bracket(FSQueue.noneTerminated[KIO, Message])(terminateQueue)
            _ <- Stream.bracket[KIO, Fiber[Throwable, Unit]](
              Consumer
                .consumeWith(consumerSettings, Subscription.Topics(Set(topic)), Serde.string, Serde.string)(enqueue(q))
                .fork
            )(_.interrupt *> logger.debugIO("Consumer fiber interrupted!"))
            //s = Stream.eval[KIO, Unit](terminateQueue(q)).delayBy(Duration(15L, SECONDS))
            r <- q.dequeue.onFinalize(logger.debugIO("Stream terminated!"))//.concurrently(s)
          } yield r).take(100)
        }
      }

      private def ok2(topic: String): KIO[Stream[KIO, Message]] =
        ZIO.bracket[ApiProvider.Env, Throwable, NoneTerminatedQueue[KIO, Message], Stream[KIO, Message]](
          acquire = FSQueue.noneTerminated[KIO, Message],
          release = terminateQueue,
          use = q =>
            for {
              fc <- Consumer
                .consumeWith(consumerSettings, Subscription.Topics(Set(topic)), Serde.string, Serde.string)(enqueue(q))
                .fork
              finalizeConsumer = fc.interrupt *> logger.debugIO("Consumer fiber interrupted and stream terminated!")
              s = q.dequeue.onFinalize(finalizeConsumer: KIO[Unit])
            } yield s
        )

      private def enqueue(q: NoneTerminatedQueue[KIO, Message])(k: String, v: String): ZIO[ApiProvider.Env, Nothing, Unit] =
        q.enqueue1(Some(Message(k, v))).foldM(
          e => logger.errorIO(s"Error pushing message: ${e.getMessage}"),
          _ => logger.debugIO(s"Pushed: ($k, $v)")
        )

      private def terminateQueue(q: NoneTerminatedQueue[KIO, Message]): ZIO[ApiProvider.Env, Nothing, Unit] =
        q.enqueue1(None).foldM(
          e => logger.errorIO(s"Error terminating queue: ${e.getMessage}"),
          _ => logger.debugIO(s"FSQueue terminated!")
        )

      private def ok3(topic: String): KIO[Stream[KIO, Message]] =
        ZStream
          .managed(Consumer.make(consumerSettings))
          .flatMap {
            _.subscribeAnd(Subscription.Topics(Set(topic)))
              .plainStream(Serde.string, Serde.string)
              .flattenChunks
              .tap(cr => logger.debugIO(s"Msg: ${cr.record.key}"))
              .map(v => Message(v.record.key, v.record.value))
          }
          .unsafeRunToStream
          .map(_.onFinalize(logger.debugIO("Stream terminated!")))

      implicit private class ZStreamToFStream[R, E <: Throwable, A](private val stream: ZStream[R, E, A]) {
        import zio.interop.reactiveStreams._
        import fs2.interop.reactivestreams.fromPublisher
        type RIO_[A_] = RIO[R, A_]
        def unsafeRunToStream: ZIO[R, E, Stream[RIO_, A]] =
          ZIO.runtime[R].map { implicit r =>
            r.unsafeRun(stream.toPublisher >>= (p => UIO(fromPublisher[RIO_, A](p))))
          }
      }

    }

  }

}