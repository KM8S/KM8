package io.km8.fx.views

import zio.*
import zio.stream.ZStream
import io.km8.common.ClusterDetails
import io.km8.fx.models.*
import io.km8.fx.models.given


trait View[S: Tag]:
  val children: List[View[S]] = Nil

  def init: ZIO[MsgBus, Nothing, Unit] =
    for
      f <- registerCallback(this, update).forkDaemon
      _ <- ZIO.foreach(children)(_.init)
    yield ()

  def update: Update

case class ViewState(
  clusterDetails: List[Cluster],
  currentCluster: Option[Cluster])

case class MainView() extends View[ViewState]:
  override val children = ClustersView() :: SearchView(None) :: Nil
  override def update = { case _ => ZIO.none}

case class ClustersView() extends View[ViewState]:
  override val children =  TitleView() :: Nil

  override def update =  {
    case Backend.Init =>
      val c1 = gen[Cluster]()
      val c2 = gen[Cluster]()
      ZIO.debug("creating clusters").as(Some(Signal.ChangedClusters(List(c1, c2))))
    case Backend.SearchClicked(search) => ZIO.debug(s"Searched $search").as(None)
    case m => ZIO.debug(s"ClusterView update: $m") *> ZIO.none
  }

case class SearchView(search: Option[String]) extends View[ViewState]:
  override def update = { case m => ZIO.debug(s"SearchView update: $m").as(None)}

case class TitleView() extends View[ViewState]:
  override def update = {case m =>  ZIO.debug(s"TitleView update: $m").as(None)}
