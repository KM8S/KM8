package io.kafkamate
package kafka
package consumer

import java.util.UUID

import api.ApiProvider
import ApiProvider._
import org.apache.kafka.clients.consumer.ConsumerConfig
import com.github.mlangc.slf4zio.api._
import cats.effect.concurrent.Deferred
import fs2.{Pull, Pure, Stream}
import fs2.concurrent.{Queue => SQueue}
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
                              cr: CommittableRecord[String, String]): Boolean = {
        endOffsets.exists(o => o._1 == cr.offset.topicPartition && o._2 == 1 + cr.offset.offset)
      }

      override def consumeStream(topic: String): KIO[Stream[KIO, Message]] =
        wip2(topic)

      private def ok1(topic: String): Stream[KIO, Message] = {
        for {
          q <- Stream.eval[KIO, SQueue[KIO, Either[Throwable, Message]]](SQueue.unbounded[KIO, Either[Throwable, Message]])
          _ <- Stream.eval[KIO, Unit] {
            Consumer.consumeWith(consumerSettings, Subscription.Topics(Set(topic)), Serde.string, Serde.string) { (k, v) =>
                q.enqueue1(Right(Message(k, v))).orDie *>
                  logger.debugIO(s"--> tap: $k")
              }.fork.unit.interruptible
          }
          r <- q.dequeue.rethrow
        } yield r
      }

      private def ok02(topic: String): Stream[KIO, Message] = {
        for {
          d <- Stream.eval(Deferred[KIO, Either[Throwable, Unit]])
          q <- Stream.eval(SQueue.noneTerminated[KIO, Message])
          f <- Stream.bracket[KIO, Fiber[Throwable, Unit]] {
            Consumer.consumeWith(consumerSettings, Subscription.Topics(Set(topic)), Serde.string, Serde.string) { (k, v) =>
              q.enqueue1(Some(Message(k, v)))
                .foldM(e => logger.errorIO(s"--> err: ${e.getMessage}"), _ => logger.debugIO(s"--> processed: $v"))
            }.fork.interruptible
          }(f => logger.debugIO("--> b1") *> f.interrupt.unit)
          //r <- q.dequeue.interruptWhen(d)
          r <- q.dequeue.onFinalize(logger.debugIO("--> suka"))
        } yield r
      }

      private def ok01(topic: String): KIO[Stream[KIO, Message]] = {
        for {
          q <- SQueue.unbounded[KIO, Either[Throwable, Message]]
          _ <- Consumer.consumeWith(consumerSettings, Subscription.Topics(Set(topic)), Serde.string, Serde.string) { (k, v) =>
              q.enqueue1(Right(Message(k, v))).orDie *>
                logger.debugIO(s"--> processed: $v")
            }.fork
        } yield q.dequeue.rethrow
      }

      private def wip3(topic: String): KIO[Stream[KIO, Message]] = {
        val program = for {
          _ <- logger.debugIO("--> starting")
          d <- Deferred[KIO, Either[Throwable, Unit]]
          q <- SQueue.noneTerminated[KIO, Message]
          fc <- Consumer.consumeWith(consumerSettings, Subscription.Topics(Set(topic)), Serde.string, Serde.string) { (k, v) =>
            q.enqueue1(Some(Message(k, v)))
              .foldM(e => logger.errorIO(s"--> err: ${e.getMessage}"), _ => logger.debugIO(s"--> processed: $v"))
          }.fork.interruptible
          s <- ZIO(q.dequeue.interruptWhen(d))
        } yield (s, fc, d)
        val z =
          ZManaged.make(program) { case (_, fc, d) =>
            (//fc.interrupt.flatMap(_.foldM(e => logger.errorIO(s"--> interrupt err: ${e.prettyPrint}"), _ => ZIO.unit))
              //*> fc.interrupt
              //*> d.complete(Left(new Error("closing stream")))
              logger.debugIO(s"--> killing from managed")).orDie
          }
          .use { case (s, _, _) =>
            //logger.debugIO("--> in use...") *> ZIO.runtime[ApiProvider.Env].map(_.unsafeRunSync(fs.join))
            logger.debugIO("--> in use...") *> UIO(s)
          }
        //z.flatMap(_.compile.toChunk.map(Stream.chunk[KIO, Message]))
        program.map(_._1).onInterrupt(logger.debugIO(s"--> killing program"))
      }

      private def wip2(topic: String): KIO[Stream[KIO, Message]] =
        Consumer.make(consumerSettings).use {
          _.subscribeAnd(Subscription.Topics(Set(topic)))
            .plainStream(Serde.string, Serde.string)
            .flattenChunks
            .tap { cr =>
              logger.debugIO(s"--> Tap: ${cr.record}")
            }
            .map(v => Message(v.record.key, v.record.value))
            .runStream
            //.flatMap(_.compile.to(fs2.Chunk).map(Stream.chunk[KIO, Message]))
        }

      implicit private class ZIOStreamToStream[R, E <: Throwable, A](private val stream: ZStream[ApiProvider.Env, E, A]) {
        import zio.interop.reactiveStreams._
        import fs2.interop.reactivestreams.fromPublisher
        def runStream[R1 <: R, E1 >: E, A0, A1 >: A, B]: ZIO[ApiProvider.Env, E1, Stream[KIO, A]] =
          ZIO.runtime[ApiProvider.Env].map { implicit r =>
            r.unsafeRun(
              stream.toPublisher >>= { publisher =>
                UIO(fromPublisher[KIO, A](publisher))
              }
            )
          }
      }

    }

  }

}