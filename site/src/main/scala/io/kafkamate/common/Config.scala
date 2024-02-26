package io.kafkamate
package common

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

object Config {

  @js.native
  @JSGlobal("KM8Config")
  private object KM8Config extends js.Object {
    val BE_HOST: String = js.native
  }

  val GrpcHost = {
    Util.logMessage(s"BE_HOST: ${KM8Config.BE_HOST}")
    KM8Config.BE_HOST
  }

}
