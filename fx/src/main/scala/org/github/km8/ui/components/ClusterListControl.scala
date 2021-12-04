package org.github.km8
package ui
package components

import scalafx.scene.control._
import zio.*
import zio.prelude.*
import org.github.km8.models.*
import scalafx.scene.Node

class ClusterListControl extends BaseControl[Has[UI]] {

  val mkList = for {
    ui <- ZIO.service[UI]
    lst = new ListView[Cluster](ui.data)
  } yield lst

  private[components] val view = mkList

  override def render: RIO[Has[UI], Node] = view
}
