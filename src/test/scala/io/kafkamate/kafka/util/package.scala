package io.kafkamate.kafka

import net.manub.embeddedkafka.{EmbeddedK, EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import zio._
import zio.blocking.Blocking
import zio.duration._
import zio.kafka.client.{Consumer, ConsumerSettings, Producer, ProducerSettings}
import zio.kafka.client.diagnostics.Diagnostics
import zio.kafka.client.serde.Serde
import zio.test.environment.TestEnvironment

package object util {

  trait Kafka {
    def kafka: Kafka.Service
  }

  object Kafka {

    trait Service {
      def bootstrapServers: List[String]

      def stop(): UIO[Unit]
    }

    case class EmbeddedKafkaService(embeddedK: EmbeddedK) extends Service {
      override def bootstrapServers: List[String] = List(s"localhost:${embeddedK.config.kafkaPort}")

      override def stop(): UIO[Unit] = ZIO.effectTotal(embeddedK.stop(true))
    }

    case object DefaultLocal extends Service {
      override def bootstrapServers: List[String] = List(s"localhost:9092")

      override def stop(): UIO[Unit] = UIO.unit
    }

    val embedded: Managed[Nothing, Kafka] =
      ZManaged.make(ZIO.effectTotal(new Kafka {
        override val kafka: Service = EmbeddedKafkaService(EmbeddedKafka.start())
      }))(_.kafka.stop())

    val local = ZIO.succeed(DefaultLocal).toManaged_
  }

  def consumerSettings(groupId: String, clientId: String): URIO[Kafka, ConsumerSettings] =
    ZIO
      .access[Kafka](_.kafka.bootstrapServers)
      .map(lst =>
        ConsumerSettings(
          bootstrapServers = lst,
          groupId = groupId,
          clientId = clientId,
          closeTimeout = 5.seconds,
          extraDriverSettings = Map(
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "earliest",
            ConsumerConfig.METADATA_MAX_AGE_CONFIG -> "100",
            ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG -> "1000",
            ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG -> "250",
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG -> "10"
          ),
          pollInterval = 250.millis,
          pollTimeout = 50.millis,
          perPartitionChunkPrefetch = 16
        )
      )

  def withConsumer[R, A](
                          groupId: String,
                          clientId: String,
                          diagnostics: Diagnostics = Diagnostics.NoOp
                        )(r: Consumer => RIO[R, A]) =
    for {
      settings <- consumerSettings(groupId, clientId)
      consumer = Consumer.make(settings, diagnostics)
      consumed <- consumer.use(r)
    } yield consumed

  def producerSettings: ZIO[Kafka, Nothing, ProducerSettings] =
    ZIO.access[Kafka](_.kafka.bootstrapServers).map(ProducerSettings(_, 30.seconds, Map()))

  def withProducer[R, A, K, V](
                                r: Producer[Any, K, V] => RIO[R, A],
                                kSerde: Serde[Any, K],
                                vSerde: Serde[Any, V]
                              ): RIO[R with Blocking with Kafka, A] =
    for {
      settings <- producerSettings
      producer = Producer.make(settings, kSerde, vSerde)
      produced <- producer.use(r)
    } yield produced

  def withProducerStrings[R, A](
                                 r: Producer[Any, String, String] => RIO[R, A]
                               ) =
    withProducer(r, Serde.string, Serde.string)

  def produceOne(t: String, k: String, m: String) =
    withProducerStrings { p =>
      p.produce(new ProducerRecord(t, k, m))
    }.flatten

  def produceMany(t: String, kvs: Iterable[(String, String)]) =
    withProducerStrings { p =>
      val records = kvs.map {
        case (k, v) => new ProducerRecord[String, String](t, k, v)
      }
      val chunk = Chunk.fromIterable(records)
      p.produceChunk(chunk)
    }.flatten

  def produceMany(topic: String, partition: Int, kvs: Iterable[(String, String)]) =
    withProducerStrings { p =>
      val records = kvs.map {
        case (k, v) => new ProducerRecord[String, String](topic, partition, null, k, v)
      }
      val chunk = Chunk.fromIterable(records)
      p.produceChunk(chunk)
    }.flatten

  def kafkaEnvironment(kafkaE: Managed[Nothing, Kafka]): Managed[Nothing, TestEnvironment with Kafka] =
    for {
      testEnvironment <- TestEnvironment.Value
      kafkaS <- kafkaE
    } yield new TestEnvironment(
      testEnvironment.blocking,
      testEnvironment.clock,
      testEnvironment.console,
      testEnvironment.live,
      testEnvironment.random,
      testEnvironment.sized,
      testEnvironment.system
    ) with Kafka {
      val kafka = kafkaS.kafka
    }

  val embeddedKafkaEnvironment: Managed[Nothing, TestEnvironment with Kafka] =
    kafkaEnvironment(Kafka.embedded)
}

