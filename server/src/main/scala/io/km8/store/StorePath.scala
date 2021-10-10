package io.km8.store

import zio._
import zio.system.System

case class StorePath(path: os.Path)

object StorePathLive:

  lazy val EnvKey   = "KM8_ENV"
  lazy val FileName = "km8.json"

  lazy val layer: RLayer[System, Has[StorePath]] =
    system
      .env(EnvKey)
      .map {
        case Some("prod") => StorePath(os.root / FileName)
        case _            => StorePath(os.pwd / FileName)
      }
      .toLayer
