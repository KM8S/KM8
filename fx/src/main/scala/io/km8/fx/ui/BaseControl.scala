package io.km8.fx
package ui

import scalafx.scene.Node
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.{Alert, ButtonType}
import zio.*

trait BaseControl[R ]:
  def render: ZIO[R, Throwable, Node]

  def alert(text: Any) =
    new Alert(AlertType.None, text.toString, ButtonType.OK).show()
