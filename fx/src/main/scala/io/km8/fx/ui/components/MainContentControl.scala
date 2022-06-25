package io.km8.fx
package ui
package components

import scalafx.scene.Node
import scalafx.scene.control.{ScrollPane, TreeItem, TreeView}
import zio.*

import models.*

class MainContentControl extends BaseControl[UI]:

  private lazy val view = ZIO.succeed(new ScrollPane())

  override def render: ZIO[UI, Throwable, Node] = view
