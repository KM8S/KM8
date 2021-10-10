package io.km8.config

import com.typesafe.config.ConfigFactory
import zio._
import zio.config._
import ConfigDescriptor._
import zio.config.typesafe._

case class AppConf(port: Int)

trait Config:
  val appConfig: AppConf

object ConfigLive:

  private lazy val appConfigDescriptor: ConfigDescriptor[AppConf] =
    nested("io") {
      nested("km8") {
        int("port")(AppConf.apply, c => Some(c.port))
      }
    }

  lazy val layer: TaskLayer[Has[Config]] =
    val eff = for {
      typesafeConf <- Task(ConfigFactory.load.resolve)
      source       <- Task.fromEither(TypesafeConfigSource.fromTypesafeConfig(typesafeConf))
      config       <- Task.fromEither(read(appConfigDescriptor from source))
    } yield new Config { val appConfig = config }

    eff.toLayer

  def getConfig: URIO[Has[Config], Config] =
    ZIO.service
