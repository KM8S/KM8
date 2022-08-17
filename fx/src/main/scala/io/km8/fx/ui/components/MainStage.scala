package io.km8.fx
package ui
package components

import zio.*
import scalafx.scene.control.SplitPane
import scalafx.scene.layout.{BorderPane, Priority, VBox}
import scalafx.scene.*
import io.km8.fx.ui.BaseControl
import scalafx.scene.input._
import scalafx.Includes.*
import views.*
import models.*

class MainStage:

  private def mkWindow =
    for
//      _ <- ZIO.debug(s"mkWindow - ${Thread.currentThread()}")
      header <- HeaderControl().render
      navigator <- NavigatorControl().render
      mainContent <- MainContentControl().render
      pane <- ZIO.attempt(new SplitPane {
        dividerPositions = 0
        id = "page-splitpane"
        items.addAll(navigator, mainContent)
      })
      p <- ZIO.attempt(new BorderPane {
        top = new VBox {
          vgrow = Priority.Always
          hgrow = Priority.Always
          children = header
        }
        center = new BorderPane {
          center = pane
        }
      })
    yield p

  def handler: Update[ViewState] =
    case m => ZIO.debug(s"UI-$m from ${Thread.currentThread()}") *> Update.none


  def render =
    for {
      given EventsQ[ViewState] <- ZIO.service[EventsQ[ViewState]]
      _ <- registerCallbackAsync(this, handler)
      sceneRoot <- mkWindow
      res <- ZIO.attempt(
        new Scene(1366, 768) {
          stylesheets = List("css/app.css")
          root = sceneRoot
          onKeyReleased = k =>
            k.code match
              case KeyCode.Slash =>
                k.consume()
                fireFX(Backend.FocusOmni)
              case _ => ()
        })
    } yield res
