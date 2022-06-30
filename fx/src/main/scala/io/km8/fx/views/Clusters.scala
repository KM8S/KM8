package io.km8.fx.views

import zio.*
import io.km8.common.ClusterDetails

trait BaseMessage

enum Msg[S] extends BaseMessage:
  case Init(state: S)

enum Screen:
  case Clusters, Search

trait View[S: Tag]:
  val children: List[View[S]] = Nil

  def init: ZIO[Hub[Msg[S]], Nothing, Unit] =
    registerCallback(update) *> ZIO.foreach(children)(_.init).unit

  def publishMessage(msg: Msg[S]): ZIO[Hub[Msg[S]], Nothing, Unit] =
    ZIO.service[Hub[Msg[S]]].flatMap(_.publish(msg)).unit

  def update(message: Msg[S]): ZIO[Any, Nothing, Option[Msg[S]]]

  def registerCallback(
    cb: Msg[S] => ZIO[Any, Nothing, Option[Msg[S]]]
  ): ZIO[Hub[Msg[S]], Nothing, Unit] =
    ZIO.scoped {
      for {
        hub <- ZIO.service[Hub[Msg[S]]]
        q <- hub.subscribe
        msg <- q.take
        res <- cb(msg)
        _ <- publishMessage(res.get).when(res.isDefined)
      } yield ()
    }

case class ViewState(
  focused: Screen,
  clusterDetails: List[ClusterDetails],
  currentCluster: Option[ClusterDetails])

case class MainView() extends View[ViewState]:
  override val children = ClustersView() :: SearchView(None) :: Nil
  override def update(message: Msg[ViewState]) = ZIO.none

case class ClustersView() extends View[ViewState]:
  override def init: UIO[Unit] = ZIO.debug("calling method from clusters")
  override def update(message: Msg[ViewState]) = ZIO.none

case class SearchView(search: Option[String]) extends View[ViewState]:
  override def init: UIO[Unit] = ZIO.debug("calling method from search")
  override def update(message: Msg[ViewState]) = ZIO.none

def initViews =
  MainView().init
