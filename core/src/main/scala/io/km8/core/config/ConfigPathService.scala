package io.km8.core
package config

import zio._
import zio.system._

lazy val EnvKey = "KAFKAMATE_ENV"
lazy val FileName = "kafkamate.json"

case class ConfigPath(path: os.Path)

lazy val liveLayer: URLayer[System, Has[ConfigPath]] =
  env(EnvKey).map {
    case Some("prod") => ConfigPath(os.root / FileName)
    case _            => ConfigPath(os.pwd / FileName)
  }.orDie.toLayer
