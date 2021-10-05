package io.km8

import zhttp.http.*
import zhttp.service.Server
import zio.*

trait Web:
  def startServer(): ZIO[Has[_], Throwable, Unit]

object Web:
  def startServer(): ZIO[Has[Web], Throwable, Unit] = ZIO.serviceWith[Web](_.startServer())

case class WebLive(config: Config) extends Web:
  val routes = Http.collectM[Request] {
    case Method.GET -> Root / "broker" / clusterId => ZIO.succeed(Response.text(s"broker $clusterId"))
  }

  override def startServer(): ZIO[Has[_], Throwable, Unit] =
    for {
      configuration <- config.getConfiguration()
      _ <- Server.start(configuration.port, routes.silent)
    } yield()

object WebLive:
  val layer: ZLayer[Has[Config], Nothing, Has[Web]] = (WebLive(_)).toLayer