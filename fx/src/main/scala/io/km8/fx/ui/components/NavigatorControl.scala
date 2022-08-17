package io.km8.fx
package ui
package components

import javafx.application.*

import scala.jdk.CollectionConverters.*
import scalafx.scene.Node
import scalafx.scene.control.*
import scalafx.Includes.*
import zio.*
import models.*
import io.km8.fx.views.*
import javafx.scene.layout.*
import javafx.scene.paint.*
import javafx.geometry.*

class NavigatorControl extends BaseControl[ViewState, MsgBus[ViewState]]:

  private val clusterNode = (cluster: Cluster) =>
    new TreeItem[String](cluster.name) {
      expanded = true
      children = Seq(
        new TreeItem[String] { value = "Topics" },
        new TreeItem[String]("Consumer Groups"),
        new TreeItem[String]("Brokers"),
        new TreeItem[String]("Settings")
      )
    }

  val update: Update[ViewState] =
    case EventData(Some(state), Some(Signal.ChangedClusters)) =>
      ZIO.attempt {
        val nodes = state.clusterDetails.map(s => clusterNode(s).delegate)
        treeView.root.value.getChildren.addAll(nodes: _*)
      }.catchAll(e => ZIO.succeed(e.printStackTrace())) *> Update.none
    case _ => Update.none

  private lazy val treeView =
    new TreeView[String] {
      showRoot = true
      id = "left-tree"
      root = new TreeItem[String]("Clusters") {
        expanded = true
      }
    }
//      _ = tv.getSelectionModel.selectedItemProperty().addListener((obs, oldVal, newVal) => alert(newVal.getValue))
/*
      _ = tv.getSelectionModel
            .selectedItemProperty()
            .addListener((obs, oldVal, newVal) => ())
*/

  lazy val scrollPane =
      new ScrollPane {
        minWidth = 300
        fitToWidth = true
        fitToHeight = true
        background = Background(BackgroundFill(Color.RED, CornerRadii(0), Insets.EMPTY))
        id = "page-tree"
        content = treeView
      }

  override def render =
    registerCallbackAsync(this, update).as(scrollPane)
