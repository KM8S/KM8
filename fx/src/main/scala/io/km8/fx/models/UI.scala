package io.km8.fx
package models

import io.km8.fx.views.ViewState
import javafx.application.Platform
import zio.*
import zio.prelude.NonEmptyList
import zio.stream.ZStream

import scala.annotation.implicitNotFound
import scala.collection.mutable.Queue as SQueue

type TestSeed = String | Int | Long

enum MessageEncoding:
  case String, Json, Binary

/*
@implicitNotFound(
  "Transition from ${P} to ${S} is not valid, add a new instance to ValidPath[${P}, ${S}] if you want to enable this")
trait ValidPath[P <: Page, S <: Page]
given ValidPath[Page.Clusters, Page.Cluster] with {}
given ValidPath[Page.Cluster, Page.Brokers] with {}
given ValidPath[Page.Cluster, Page.Topics] with {}

case class Path[P <: Page, S <: Page](p: P, S: S)(using ValidPath[P, S])

extension [P <: Page](p: P)
  def `/`[S <: Page](s: S)(using ValidPath[P, S]) = Path[P, S](p, s)

sealed trait Page

object Page:
  case class Clusters() extends Page
  case class Cluster() extends Page
  case class Brokers() extends Page
  case class Topics() extends Page
  case class ConsumerGroups() extends Page
  case class ClusterSettings() extends Page
  case class Topic() extends Page
  case class Messages() extends Page
  case class Broker() extends Page

  val clusters = Clusters()
  val cluster = Cluster()
  val brokers = Brokers()
  val topics = Topics()
  val consumerGroups = ConsumerGroups()
  val clusterSettings = ClusterSettings()
  val topic = Topic()
  val messages = Messages()
  val broker = Broker()

val a = Page.clusters / Page.cluster
val b = Page.cluster / Page.brokers
val c = Page.cluster / Page.topics
val d = Page.cluster / Page.broker

def switch[P <: Page, S <: Page](p: Path[P, S]) = p match {
  case a: Path[Page.Clusters, Page.Cluster] => ???
}
 */

//inline def isNotEmpty: Assertion[String] = hasLength(greaterThan(0))

opaque type Offset = Long

opaque type Config = (String, String)

opaque type TopicName = String

/*
  Default type class
 */
trait Def[T]:
  def apply(seed: TestSeed): T

given Def[TopicName] with
  def apply(seed: TestSeed): TopicName = s"topic $seed"

def gen[T: Def](seed: TestSeed = "test"): T = summon[Def[T]].apply(seed)

case class Broker(id: String, configs: List[Config])

given Def[Broker] with
  def apply(seed: TestSeed) = Broker(seed.toString, Nil)

case class Host(address: String, port: Int)

given Def[Host] with
  def apply(seed: TestSeed) = Host(seed.toString, 0)

case class Replica(host: Host, inSync: Boolean)

given Def[Replica] with
  def apply(seed: TestSeed) = Replica(gen(seed), false)

case class Partition(
  index: Int,
  leader: Host,
  replicas: NonEmptyList[Replica],
  startOffset: Offset,
  endOffset: Offset,
  size: Long)

given Def[Partition] with
  def apply(seed: TestSeed) = Partition(0, gen(seed), NonEmptyList(gen(seed)), 0L, 0L, 0)

case class Topic(
  name: TopicName,
  partitions: NonEmptyList[Partition],
  configs: List[Config],
  keyEncoding: MessageEncoding,
  valueEncoding: MessageEncoding)

given Def[Topic] with

  def apply(seed: TestSeed) =
    Topic(
      s"topic $seed",
      NonEmptyList(gen(seed)),
      Nil,
      MessageEncoding.String,
      MessageEncoding.String
    )

case class PartitionOffset(partition: Partition, offset: Offset)

given Def[PartitionOffset] with
  def apply(seed: TestSeed) = PartitionOffset(gen(seed), 0L)

case class ConsumerGroup(
  name: String,
  topic: TopicName,
  partitionOffsets: List[PartitionOffset])

given Def[ConsumerGroup] with

  def apply(seed: TestSeed) =
    ConsumerGroup(s"name $seed", gen(seed), List.range(0, 10).map(gen))

given Def[Config] with
  def apply(seed: TestSeed): Config = s"key_$seed" -> s"value $seed"

case class Cluster(
  name: String,
  kafkaHosts: NonEmptyList[Host],
  zkHosts: NonEmptyList[Host],
  brokers: NonEmptyList[Broker],
  topics: List[Topic],
  consumerGroups: List[ConsumerGroup],
  config: List[Config])

given Def[Cluster] with

  def apply(seed: TestSeed) =
    Cluster(
      name = s"Cluster $seed",
      kafkaHosts = NonEmptyList(gen(seed)),
      zkHosts = NonEmptyList(gen(seed)),
      brokers = NonEmptyList(gen(seed)),
      topics = List(gen(seed)),
      consumerGroups = List(gen(seed)),
      config = List(gen(seed))
    )

case class UIConfig(leftWidth: Int)

case class UI(
  data: List[Cluster],
  config: UIConfig)

object UI:

  def make(seed: TestSeed = ""): UI =
    UI(
      data = List.range(1, 4).map(gen),
      config = UIConfig(leftWidth = 300)
    )

  def makeLayer(seed: TestSeed = ""): URLayer[Any, UI] =
    ZLayer.succeed(
      UI(
        data = List.range(1, 4).map(gen),
        config = UIConfig(leftWidth = 300)
      )
    )

case class Message(
  key: Array[Byte],
  value: Array[Byte],
  headers: List[MessageHeader])

case class MessageHeader(key: String, value: Array[Byte])

object Test {
  val stuff: String = ""
}

trait Msg

enum Backend extends Msg:
  case LoadClusters
  case LoadConfig
  case FocusOmni
  case Search(search: String)
  case LoadTopics
  case KeyPressed(key: Char)

enum Signal extends Msg:
  case Nop
  case Search
  case ChangedClusters

case class EventData[+S](state: Option[S], msg: Option[Msg])

object EventData:
  def apply[S](state: S): EventData[S] = EventData(Some(state), None)
  def apply(msg: Msg): EventData[Nothing] = EventData(None, Some(msg))

type MsgBus[S] = Hub[EventData[S]]

object MsgBus:
  def layer[S: Tag] = ZLayer.fromZIO(Hub.unbounded[(Option[S], Option[Msg])])

  def signal[S: Tag](m: Msg, s: S) =
    ZIO.service[MsgBus[S]].flatMap { hub =>
      hub.publish(EventData(Some(s), Some(m)))
    }

type EventsQ[S] = SQueue[EventData[S]]

def fireFX[S](m: Option[Msg], s: Option[S]): EventsQ[S] ?=> Unit =
  summon[EventsQ[S]].enqueue(EventData(s, m))

def fireFX[S](m: Msg, s: Option[S] = None): EventsQ[S] ?=> Unit =
  fireFX(Some(m), s)

import io.km8.fx.views.*

object App:
  val schedule = Schedule.spaced(100.millis)

  def initialize[S: Tag, V <: View[S]](v: View[S]): UIO[(ULayer[MsgBus[S]], ULayer[EventsQ[S]])] =
    for
      hub <- Hub.unbounded[EventData[S]]
      hubLayer = ZLayer.succeed(hub)
      _ <- v.init.forkDaemon.provide(hubLayer)
      eventsQ <- ZIO.succeed(SQueue.empty[EventData[S]])
      _ <- ZIO
             .succeed(eventsQ.dequeueAll(_ => true))
             .flatMap(hub.publishAll)
             .repeat(schedule)
             .forkDaemon
    yield (hubLayer, ZLayer.succeed(eventsQ))

extension (c: => Unit) def fx = ZIO.succeed(Platform.runLater(() => c))

type Update[S] = EventData[S] => UIO[EventData[S]]

object Update:
  def state[S](state: S): UIO[(Option[S], Option[Msg])] =
    ZIO.succeed(Some(state), None)

  def stateZIO[S](state: UIO[S]): UIO[(Option[S], Option[Msg])] =
    state.map(s => Some(s) -> None)

  def apply[S](state: S, msg: Msg): UIO[EventData[S]] =
    ZIO.succeed(EventData(Some(state), Some(msg)))

  def none[S]: UIO[EventData[S]] = ZIO.succeed(EventData(None , None))

def publishMessage[S: Tag](msg: EventData[S]): URIO[MsgBus[S], Unit] =
  ZIO.service[MsgBus[S]].flatMap(_.publish(msg)).unit

def registerCallbackAsync[S: Tag](sender: Object, cb: Update[S]) =
  registerCallback(sender, cb).forkDaemon

def registerCallback[S: Tag](sender: Object, cb: Update[S]): URIO[MsgBus[S], Unit] =
  for
    hub <- ZIO.service[MsgBus[S]]
    _ <- ZIO.debug(s"registering ${sender.toString} - ${Thread.currentThread()}")
    _ <- ZStream.fromHub(hub).foreach { m =>
           for
             res <- cb(m)
             _ <- res match
                    case EventData(s, m) if s.isDefined || m.isDefined =>
                      ZIO.debug(s"Sending $res") *> hub.publish(res)
                    case _    => Update.none

           yield ()
         }
  yield ()
