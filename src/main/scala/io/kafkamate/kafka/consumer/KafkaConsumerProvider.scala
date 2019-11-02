package io.kafkamate
package kafka
package consumer

import java.util.UUID

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.kafka.client.serde.Serde
import zio.kafka.client.{CommittableRecord, Consumer, ConsumerSettings, OffsetBatch, Subscription}
import zio.stream.ZSink

trait KafkaConsumerProvider {
  def kafkaConsumer: KafkaConsumerProvider.Service
}

object KafkaConsumerProvider {

  trait Env extends Clock with Blocking

  trait Service {
    def consume: RIO[Env, List[(String, String)]]
  }

  trait LiveConsumer extends KafkaConsumerProvider {

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

    private val timeout = 1000.millis

    def kafkaConsumer: KafkaConsumerProvider.Service = new Service {
      def consume: RIO[Env, List[(String, String)]] = {
        //cons0
        cons01
        //cons2
      }

      def cons0: ZIO[Env, Throwable, List[(String, String)]] = {
        Consumer.make(consumerSettings).use {
          _.subscribeAnd(Subscription.Topics(Set("test")))
            .plainStream(Serde.string, Serde.string)
            .flattenChunks
            .tap { cr =>
              ZIO(println(s"----> record: ${cr.record.value()}"))
            }
            .take(1)
            .run(ZSink.collectAll[CommittableRecord[String, String]])
            //.map(_.map(v => (v.record.key, v.record.value)))
            .flatMap(recs => recs.foldLeft(OffsetBatch.empty)((b, r) => b merge r.offset).commit.as(recs.map(v => (v.record.key, v.record.value))))
        }
      }

      def cons01: RIO[Env, List[(String, String)]] = {
        Consumer.make(consumerSettings).use { c =>
          for {
            _ <- c.subscribe(Subscription.Topics(Set("test")))
            endOffsets <- c.assignment.repeat(Schedule.doUntil(_.nonEmpty)).flatMap(c.endOffsets(_, timeout))
            _ <- Task(println(s"----> End offsets: $endOffsets"))
            stream = c
              .plainStream(Serde.string, Serde.string)
              .flattenChunks
              .takeUntil(cr => endOffsets.exists(o => o._1 == cr.offset.topicPartition))
            lst <- stream
              .run(ZSink.collectAll[CommittableRecord[String, String]])
              .flatMap(recs => recs.foldLeft(OffsetBatch.empty)((b, r) => b merge r.offset).commit.as(recs.map(v => (v.record.key, v.record.value))))
          } yield lst
        }
      }

      def cons1: RIO[Env, Unit] = {
        val subscription = Subscription.topics("test")
        Consumer.consumeWith(consumerSettings, subscription, Serde.string, Serde.string) { case (key, value) =>
          UIO(println(s"----> Received message: $key: $value"))
        }
      }

      def cons2: RIO[Env, List[(String, String)]] = {
        Consumer.make(consumerSettings).use { consumer =>
          consumer.subscribe(Subscription.Topics(Set("test"))) *> {
            val readToEnd: ZIO[Env, Throwable, List[(String, String)]] = for {
              endOffsets  <- consumer.assignment.repeat(Schedule.doUntil(_.nonEmpty)).flatMap(consumer.endOffsets(_, 1000.millis))
              _           <- consumer.seekToBeginning(endOffsets.keys.toSet)
              _           <- Task(println(s"----> Loop Start: ${endOffsets.toString}"))
              finishLine  <- Ref.make[Map[TopicPartition, Boolean]](endOffsets.keys.map(_ -> false).toMap)
              _           <- finishLine.get.flatMap(fl => Task(println(s"----> Finish Line Is: $fl")))
              stream      =  consumer.plainStream(Serde.string, Serde.string).flattenChunks
              logs        <- stream.run(ZSink.collectAllWhileM { r: CommittableRecord[String, String] =>
                for {
                  _                     <- Task(println(s"----> Current Item: ${r.record}"))
                  updatedFinishingLine  <- finishLine.update(_ + (r.offset.topicPartition -> endOffsets.exists{case (t, o) => r.offset.topicPartition.equals(t) && r.offset.offset + 1 == o}))
                  _                     <- Task(println(s"----> Updated Finishing Line: $updatedFinishingLine"))
                } yield !updatedFinishingLine.values.forall(b => b)
              }).flatMap(recs => recs.foldLeft(OffsetBatch.empty)((b, r) => b merge r.offset).commit.as(recs.map(v => (v.record.key, v.record.value))))
            } yield logs
            readToEnd
          }
        }
      }
    }

  }

}