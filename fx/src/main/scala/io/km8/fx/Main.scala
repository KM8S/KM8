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
import io.km8.fx.models.{*, given}
import io.km8.fx.ui.{*, given}
import io.km8.fx.ui.components.{*, given}
import io.km8.fx.views.{*, given}

import scala.concurrent.ExecutionContext
import scalafx.scene.input.KeyEvent
import scalafx.Includes.*
import scalafx.scene.input.KeyCode

object Main extends JFXApp3:

  val currentExe =
    zio.Executor.fromJavaExecutor((command: Runnable) => command.run())

  override def start(): Unit =

    val io =
      for
        layers <- App.initialize(MainView)
        (msgLayer, fxLayer) = layers
        s <- MainStage()
          .render
          .tapError(e => ZIO.succeed(e.printStackTrace))
          .orDie
          .provide(msgLayer, fxLayer)
        ret = new JFXApp3.PrimaryStage {
                title = s"KM8 - ${Thread.currentThread()}"
                scene = s
              }
        _ <- MsgBus.signal(Backend.LoadClusters, ViewState.empty).provide(msgLayer)
      yield ret

    stage =
      Unsafe.unsafeCompat { implicit u: Unsafe =>
        Runtime.default.unsafe.run(io.onExecutor(currentExe)).getOrThrow()
      }
