package io.km8.fx
package ui
package components

import scalafx.scene.Node
import scalafx.scene.control.{ScrollPane, TreeItem, TreeView}
import zio.*

import models.*

class MainContentControl extends BaseControl[Has[UI]]:

  private lazy val view = ZIO(new ScrollPane())

  override def render: ZIO[Has[UI], Throwable, Node] = view
