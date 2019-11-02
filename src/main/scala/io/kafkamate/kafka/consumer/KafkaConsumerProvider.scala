package io.kafkamate
package kafka
package consumer

import java.util.UUID

import org.apache.kafka.clients.consumer.ConsumerConfig
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.kafka.client._
import zio.kafka.client.serde.Serde
import zio.stream.ZSink

trait KafkaConsumerProvider {
  def kafkaConsumer: KafkaConsumerProvider.Service
}

object KafkaConsumerProvider {

  trait Env extends Clock with Blocking

  trait Service {
    def consume(topic: String): RIO[Env, List[(String, String)]]
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
      def consume(topic: String): RIO[Env, List[(String, String)]] =
        Consumer.make(consumerSettings).use { c =>
          for {
            _ <- c.subscribe(Subscription.Topics(Set(topic)))
            endOffsets <- c.assignment.repeat(Schedule.doUntil(_.nonEmpty)).flatMap(c.endOffsets(_, timeout))
            _ <- Task(println(s"----> End offsets: $endOffsets"))
            stream = c
              .plainStream(Serde.string, Serde.string)
              .flattenChunks
              .takeUntil(cr => endOffsets.exists(o => o._1 == cr.offset.topicPartition && o._2 == 1 + cr.offset.offset))
            records <- stream
              .run(ZSink.collectAll[CommittableRecord[String, String]])
              .flatMap { recs =>
                recs
                  .foldLeft(OffsetBatch.empty)((b, r) => b merge r.offset)
                  .commit
                  .as(recs.map(v => (v.record.key, v.record.value)))
              }
          } yield records
        }
    }

  }

}