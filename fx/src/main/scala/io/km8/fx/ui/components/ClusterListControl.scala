package io.km8.fx
package ui
package components

import scalafx.scene.control.*
import zio.*
import zio.prelude.*
import scalafx.scene.Node

import models.*
import views.*

class ClusterListControl extends BaseControl[ViewState, UI & MsgBus[ViewState]] {

  val mkList = for {
    ui <- ZIO.service[UI]
    lst = new ListView[Cluster](ui.data)
  } yield lst

  private[components] val view = mkList

  override def render =
    registerCallbackAsync(this, update ) *> view

  val update: Update[ViewState] =
    case s => ZIO.debug(s"${this.getClass} - $s").as(None)
}
