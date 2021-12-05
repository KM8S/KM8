package io.km8.fx
package ui
package components

import scalafx.geometry.*
import scalafx.scene.Node
import scalafx.scene.control.*
import scalafx.scene.image.{Image, ImageView}
import zio.*

import models.*

class HeaderControl extends BaseControl[Has[UI]]:

  lazy val button = new Button {
    text = "Do Somethign"
    onMouseClicked = _ => alert("test")
  }

  private[ui] val view = ZIO(new ToolBar {
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
        margin = Insets(0, 0, 0, 10)
      },
      button
    )
  })

  override def render: RIO[Has[UI], Node] = view
