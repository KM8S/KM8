package io.km8.fx
package ui
package components

import scalafx.scene.control.*
import zio.*
import zio.prelude.*
import scalafx.scene.Node

import models.*

class ClusterListControl extends BaseControl[Has[UI]] {

  val mkList = for {
    ui <- ZIO.service[UI]
    lst = new ListView[Cluster](ui.data)
  } yield lst

  private[components] val view = mkList

  override def render: RIO[Has[UI], Node] = view
}
