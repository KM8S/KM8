package io.km8.fx

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
import io.km8.fx.ui.{given, *}
import io.km8.fx.ui.components.{given, *}

import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext

object Main extends JFXApp3 with BootstrapRuntime:

  val currentThreadEC = ExecutionContext.fromExecutor(new Executor {
    override def execute(command: Runnable): Unit = command.run()
  })

  private lazy val mkWindow =
    for
      header <- HeaderControl().render
      navigator <- NavigatorControl().render
      mainContent <- MainContentControl().render
      pane <- ZIO(new SplitPane {
        dividerPositions = 0
        id = "page-splitpane"
        items.addAll(navigator, mainContent)
      })
      p <- ZIO(new BorderPane {
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

  override def start(): Unit =
    val io =
      for
        main <- mkWindow
        ret <- ZIO(
          new JFXApp3.PrimaryStage {
            title = "KM8"
            scene = new Scene(1366, 768) {
              stylesheets = List("css/app.css")
              root = main
            }
          })
      yield ret
    unsafeRun(
      io
        .provideCustomLayer(ZLayer.succeed(gen[UI]("")))
        .on(currentThreadEC)
        .exitCode
    )
