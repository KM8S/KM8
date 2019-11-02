package io.kafkamate
package util

import api.ApiProvider
import kafka.consumer.KafkaConsumerProvider
import http.HttpServerProvider
import zio._
import zio.blocking.Blocking
import zio.clock.Clock

trait LiveEnv
  extends HttpServerProvider.Env
  with HttpServerProvider.LiveHttpServer
  with KafkaConsumerProvider.LiveConsumer

object LiveEnv {

  def Live(rtm: Runtime[ZEnv]): LiveEnv = new LiveEnv { self =>
    val ApiEnv: ApiProvider.Env = new ApiProvider.Env {
      val clock: Clock.Service[Any] = rtm.Environment.clock
      val blocking: Blocking.Service[Any] = rtm.Environment.blocking
      def kafkaConsumer: KafkaConsumerProvider.Service = self.kafkaConsumer
    }
    def apiProvider: ApiProvider.Service = new ApiProvider.LiveApi {
      val runtime: Runtime[ApiProvider.Env] = Runtime(ApiEnv, rtm.Platform)
    }
  }

}
