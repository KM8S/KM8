package io.km8.fx

import com.sun.javafx.application.PlatformImpl
import javafx.application.Platform

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
import zio.stream.ZStream
import scala.collection.mutable.Queue as SQueue

import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext
import scalafx.scene.input.KeyEvent
import scalafx.Includes.*
import scalafx.scene.input.KeyCode

object Main extends JFXApp3:

  val currentExe = zio.Executor.fromJavaExecutor((command: Runnable) => command.run())
  val currentThreadEC = ExecutionContext.fromExecutor((command: Runnable) => command.run())
  lazy val headerControl = HeaderControl()

  private def mkWindow =
    for
      q <- ZIO.service[EventsQ]
      _ <- ZIO.debug(s"mkWindow - ${Thread.currentThread()}")
      header <- headerControl.render
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

  private def mkScene(sceneRoot: Parent) =
    for {
      q <- ZIO.service[EventsQ]
      res <- ZIO.attempt(
        new Scene(1366, 768) {
          stylesheets = List("css/app.css")
          root = sceneRoot
          onKeyReleased = k =>
            k.code match
              case KeyCode.Slash =>
                k.consume()
                q.enqueue(Backend.FocusOmni)
              case _ => ()
        })
   } yield res

  def handler: Update =
    case m =>
        ZIO.debug(s"UI-$m from ${Thread.currentThread()}").as(None)

  override def start(): Unit = {
    val ui = UI.make()
    val io =
      for
        hub <- Hub.unbounded[Msg]
        msgLayer = ZLayer.succeed(hub)
        qLayer = msgLayer >>> EventsQ.toBus
        _ <- MainView().init.forkDaemon.provide(msgLayer)
        _ <- registerCallback(this, handler).provide(msgLayer).forkDaemon
        _ <-
              ZIO.debug(s"Init - ${Thread.currentThread()}") *> hub
                .publish(Backend.Init)
        main <- mkWindow.provide(ZLayer.succeed(ui), qLayer, msgLayer).onExecutionContext(currentThreadEC)
        s <- mkScene(main).provide(qLayer)
        ret <-
          ZIO.attempt {
            println(s"KM8 - ${Thread.currentThread()} - ${Platform.isFxApplicationThread}")
            new JFXApp3.PrimaryStage {
              title = s"KM8 - ${Thread.currentThread()} - ${Platform.isFxApplicationThread}"
              scene = s
            }
          }
      yield ret

    stage =
      Unsafe.unsafeCompat { implicit u: Unsafe =>
        Runtime.default.unsafe.run(io.onExecutor(currentExe)).getOrThrow()
      }

  }
