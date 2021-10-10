package io.km8.http

import zhttp.http._
import zhttp.service.Server
import zio.logging._
import zio._

import io.km8.config.Config

trait Web:
  def startServer: Task[Unit]

object Web extends Accessible[Web]

case class WebLive(config: Config, logger: Logger[String]) extends Web:

  private val routes = Http.collectM[Request] {
    case Method.GET -> Root / "broker" / clusterId =>
      UIO(Response.text(s"broker $clusterId"))
    case Method.GET -> Root / "topics" =>
      UIO(Response.text(s"topics"))
  }

  override def startServer: Task[Unit] =
    for {
      _ <- logger.info(s"Starting server, port: ${config.appConfig.port}")
      _ <- Server.start[Has[Config]](config.appConfig.port, routes.silent).provide(Has(config))
    } yield ()

object WebLive:
  val layer: URLayer[Has[Config] & Logging, Has[Web]] = (WebLive(_, _)).toLayer
