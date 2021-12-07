package io.km8.fx
package ui
package components

import scalafx.geometry.*
import scalafx.scene.Node
import scalafx.scene.control.*
import scalafx.scene.image.{Image, ImageView}
import zio.*
import zio.stream.*

import models.*
import scalafx.beans.property.ReadOnlyDoubleProperty

class HeaderControl extends BaseControl[UIEnv]:

  lazy val omniBar =
    new TextField {
      id = "omni"
      prefWidth = 600
    }

  lazy val button = new Button {
    id = "search-omni"
    text = "Search"
    onMouseClicked = _ => alert("test")
  }

  private[ui] val view =
    for
      hub <- ZIO.service[EventsHub]
      _ <- ZStream
             .fromHub(hub)
             .foreach { case UIEvent.FocusOmni =>
               ZIO {
                 omniBar.requestFocus()
               }
             }
             .forkDaemon
    yield new ToolBar {
      prefHeight = 76
      maxHeight = 76
      id = "mainToolBar"
      content = List(
        new ImageView {
          image = new Image(
            this.getClass.getResourceAsStream("/images/logo.png"),
            200,
            100,
            true,
            true
          )
          margin = Insets(0, 100, 0, 10)
        },
        omniBar,
        button
      )
    }

  override def render: RIO[UIEnv, Node] = view
