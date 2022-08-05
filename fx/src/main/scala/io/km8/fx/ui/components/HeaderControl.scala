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

class HeaderControl extends BaseControl[ViewState, MsgBus[ViewState] & EventsQ[ViewState]]:

  lazy val omniBar =
    new TextField {
      id = "omni"
      prefWidth = 600
    }

  val buttonZIO =
    for {
      given EventsQ[ViewState] <- ZIO.service[EventsQ[ViewState]]
      button <- ZIO.attempt(new Button {
        id = "search-omni"
        text = "Search"
        onMouseClicked = _ =>
            fireFX(Backend.Search(omniBar.text.value))
      })
    } yield button

  val update: Update[ViewState] = {
    case _ -> Backend.FocusOmni => omniBar.requestFocus().fx.as(None)
    case _ => ZIO.none
  }

  override def render =
    for {
      button <- buttonZIO
      toolBar =
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
        }
      _ <- registerCallbackAsync(this, update)
    } yield toolBar
