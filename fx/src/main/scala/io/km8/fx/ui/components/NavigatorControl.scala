package io.km8.fx
package ui
package components

import scalafx.scene.Node
import scalafx.scene.control.{ScrollPane, TreeItem, TreeView}
import zio.*
import models.*

class NavigatorControl extends BaseControl[Has[UI]]:

  private lazy val view =
    for
      ui <- ZIO.service[UI]
      tv <- treeView
      ret <- ZIO(
               new ScrollPane {
                 minWidth = ui.config.leftWidth
                 fitToWidth = true
                 fitToHeight = true
                 id = "page-tree"
                 content = tv
               }
             )
    yield ret

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

  private val treeView =
    for
      ui <- ZIO.service[UI]
      tv <- ZIO {
              new TreeView[String] {
                showRoot = false
                id = "left-tree"
                root = new TreeItem[String]("Clusters") {
                  expanded = true
                  children = ui.data.map(clusterNode).toSeq
                }
              }
            }
//      _ = tv.getSelectionModel.selectedItemProperty().addListener((obs, oldVal, newVal) => alert(newVal.getValue))
      _ = tv.getSelectionModel
            .selectedItemProperty()
            .addListener((obs, oldVal, newVal) => alert(ui.data.map(_.consumerGroups)))
    yield tv

  override def render: ZIO[Has[UI], Throwable, Node] = view
