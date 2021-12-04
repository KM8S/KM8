package org.github.km8.ui.components

import org.github.km8.models.*
import org.github.km8.ui.BaseControl
import scalafx.scene.Node
import scalafx.scene.control.{ScrollPane, TreeItem, TreeView}
import zio.*

class MainContentControl extends BaseControl[Has[UI]]:

  private lazy val view = ZIO(new ScrollPane())

  override def render: ZIO[Has[UI], Throwable, Node] = view
