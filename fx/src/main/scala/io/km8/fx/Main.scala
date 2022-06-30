package io.km8.fx

import javafx.application.Platform

import scala.collection.mutable.Queue as SQueue
import zio.*
import javafx.beans.property.ObjectProperty
import scalafx.application.JFXApp3
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.*
import scalafx.scene.control.*
import scalafx.scene.layout.*
import scalafx.scene.paint.*
import scalafx.scene.text.*
import io.km8.fx.models.*
import io.km8.fx.models.given
import io.km8.fx.ui.{*, given}
import io.km8.fx.ui.components.{*, given}
import io.km8.fx.views.{*, given}

import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext
import scalafx.scene.input.KeyEvent
import scalafx.Includes.*
import scalafx.scene.input.KeyCode

object Main extends JFXApp3:

  val currentThreadEC = ExecutionContext.fromExecutor(new Executor {
    override def execute(command: Runnable): Unit = command.run()
  })

  private lazy val mkWindow =
    for
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

  private def mkScene(sceneRoot: Parent): ZIO[EventsHub, Throwable, Scene] =
    for dispatchFocusOmni <- dispatchEvent
    yield new Scene(1366, 768) {
      stylesheets = List("css/app.css")
      root = sceneRoot
      onKeyReleased = k =>
        k.code match
          case KeyCode.Slash =>
            k.consume()
            dispatchFocusOmni(UIEvent.FocusOmni)
          case _ => ()
    }

  override def start(): Unit =
    val io: ZIO[Any, Throwable, JFXApp3.PrimaryStage] =
      ZIO.scoped {
        val hubLayer = ZLayer.fromZIO(Hub.unbounded[UIEvent])
        val hubMessages = ZLayer.fromZIO(Hub.unbounded[Msg[ViewState]])
        for
          _ <- initViews.provide(hubMessages)
          main <- mkWindow.provideLayer(UI.make("") +!+ hubLayer)
          s <- mkScene(main).provideLayer(hubLayer)
          ret <- ZIO.attempt(new JFXApp3.PrimaryStage {
                   title = "KM8"
                   scene = s
                 })
        yield ret
      }

    Unsafe.unsafe { implicit u: Unsafe =>
      Runtime.default.unsafe.run(
        io
          .onExecutionContext(currentThreadEC)
          .exitCode
      )
    }
