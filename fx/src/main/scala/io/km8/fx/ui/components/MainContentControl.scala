package io.km8.fx
package ui
package components

import scalafx.scene.Node
import scalafx.scene.control.{ScrollPane, TreeItem, TreeView}
import zio.*

import models.*
import io.km8.fx.views.*

class MainContentControl extends BaseControl[ViewState, MsgBus[ViewState]]:

  private lazy val view = ZIO.attempt(new ScrollPane())

  override def render =
    registerCallbackAsync(this, update) *> view

  val update: Update[ViewState] =
    case (_, m) => ZIO.debug(s"${this.getClass} - $m") *> Update.none
