package io.kafkamate
package util

import api.ApiProvider
import http.HttpServerProvider
import zio._

trait LiveEnv extends HttpServerProvider.Env with HttpServerProvider.LiveHttpServer

object LiveEnv {
  def Live(r: Runtime[ZEnv]): LiveEnv = new LiveEnv {
    def apiProvider: ApiProvider.Service = new ApiProvider.LiveApi {
      val runtime: Runtime[zio.ZEnv] = r
    }
  }
}
