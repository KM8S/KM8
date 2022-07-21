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
import io.km8.fx.views.*
import javafx.application.Platform

class HeaderControl extends BaseControl[UIEnv & MsgBus & EventsQ]:

  lazy val omniBar =
    new TextField {
      id = "omni"
      prefWidth = 600
    }

  lazy val buttonZIO =
    for {
      q <- ZIO.service[EventsQ]
      button <- ZIO.attempt(new Button {
        id = "search-omni"
        text = "Search"
        onMouseClicked =
          _ => q.enqueue(Backend.SearchClicked(omniBar.text.value))
      })
    } yield button

  val update: Update =
    case Backend.FocusOmni =>
      ZIO.debug("Focus omni") *>
         omniBar.requestFocus().fx.as(None)

  private[ui] lazy val view =
    for {
      button <- buttonZIO
      toolBar <- ZIO.attempt(
        new ToolBar {
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
        })
      _ <- registerCallback(this, update).forkDaemon
    } yield toolBar

  override def render = view
