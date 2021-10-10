package io.km8

import zhttp.http._
import zhttp.service.Server
import zio.logging._
import zio._

trait Web:
  def startServer: Task[Unit]

object Web extends Accessible[Web]

case class WebLive(settings: Settings, logger: Logger[String]) extends Web:

  private val routes = Http.collectM[Request] {
    case Method.GET -> Root / "broker" / clusterId =>
      UIO(Response.text(s"broker $clusterId"))
    case Method.GET -> Root / "topics" =>
      UIO(Response.text(s"topics"))
  }

  override def startServer: Task[Unit] =
    for {
      _ <- logger.debug(s"Starting server, port: ${settings.appConfig.port}")
      _ <- Server.start[Has[Settings]](settings.appConfig.port, routes.silent).provide(Has(settings))
    } yield ()

object WebLive:
  val layer: URLayer[Has[Settings] & Logging, Has[Web]] = (WebLive(_, _)).toLayer
