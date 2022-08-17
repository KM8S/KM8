package io.km8.fx.views

import zio.*
import zio.stream.ZStream
import io.km8.common.ClusterDetails
import io.km8.fx.models.*
import io.km8.fx.models.given

object Data:

  def loadClusters =
    ZIO.succeed {
      val c1 = gen[Cluster]()
      val c2 = gen[Cluster]()
      c1 :: c2 :: Nil
    }

trait View[S: Tag]:
  val children: List[View[S]] = Nil

  def init: ZIO[MsgBus[S], Nothing, Unit] =
    for
      _ <- registerCallbackAsync(this, update)
      _ <- ZIO.foreach(children)(_.init)
    yield ()

  def update: Update[S]

case class ViewState(
  clusterDetails: List[Cluster],
  currentCluster: Option[Cluster])

object ViewState:
  def empty = ViewState(Nil, None)

object MainView extends View[ViewState]:
  override val children = ClustersView :: SearchView :: Nil

  override def update =
    case _ => Update.none

object ClustersView extends View[ViewState]:
  override val children = TitleView :: Nil

  override def update =
    case EventData(Some(state), Some(Backend.LoadClusters)) =>
      Data.loadClusters.flatMap { newClusters =>
        ZIO
          .debug("creating clusters") *>
          Update(state.copy(clusterDetails = newClusters), Signal.ChangedClusters)
      }
    case EventData(_, Some(Backend.Search(search))) =>
      ZIO.debug(s"Searched $search") *> Update.none
    case EventData(_, m) =>
      ZIO.debug(s"ClusterView update: $m") *> Update.none

/*

object TextInput extends View[String]:
  override def update =
    case state -> Backend.KeyPressed(k) =>
      ZIO.succeed(state ++ 'c'.toString).
*/


object SearchView extends View[ViewState]:

  override def update =
    case EventData(_, m) => ZIO.debug(s"SearchView update: $m") *> Update.none

object TitleView extends View[ViewState]:

  override def update =
    case EventData(_ , m) => ZIO.debug(s"TitleView update: $m") *> Update.none
