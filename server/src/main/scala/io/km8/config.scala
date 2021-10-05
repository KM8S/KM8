package io.km8
import com.typesafe.config.ConfigFactory
import zio.*
import zio.config.*
import ConfigDescriptor._
import zio.config.typesafe.*


case class Configuration(port: Int)

trait Config:
  def getConfiguration(): Task[Configuration]

case class ConfigLive() extends Config:
  private val configDescriptor: ConfigDescriptor[Configuration] = nested("io") {
    nested("km8"){
      // because the unapply function has changed in Scala 3, I re-created it here
      val u: Configuration => Option[Int] = c => Some(c.port)
      (int("port"))(Configuration.apply, u)
    }
  }
  private val configSource = TypesafeConfigSource.fromTypesafeConfig(ConfigFactory.load.resolve) match {
    case Left(e) => throw new Exception(e.getMessage())
    case Right(value) => value
  }

  override def getConfiguration(): Task[Configuration] =
    ZIO.fromEither(read(configDescriptor.from(configSource)))

object ConfigLive {
 val layer: ZLayer[Any, Throwable, Has[Config]] = ( () => ConfigLive()).toLayer
}