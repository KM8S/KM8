package io.kafkamate.bridges.reactrouter

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@JSImport("react-router-dom", JSImport.Default)
@js.native
object ReactRouterDOM extends js.Object {
  def useParams(): js.Dictionary[String] = js.native
  def useLocation(): Location            = js.native

  trait Location extends js.Object {
    val key: String
    val pathname: String
    val search: String
    val hash: String
  }
}
