package io.kafkamate.bridges.reactrouter

import scala.scalajs.js
import scala.scalajs.js.{UndefOr, |}

import slinky.core.ExternalComponent
import slinky.core.annotations.react
import slinky.reactrouter.ReactRouterDOM

case class To(
  pathname: Option[String] = None,
  search: Option[String] = None,
  hash: Option[String] = None,
  state: Option[js.Object])

@react object NavLink extends ExternalComponent {

  case class Props(
    to: String | To,
    exact: UndefOr[Boolean] = js.undefined,
    activeClassName: UndefOr[String] = js.undefined,
    activeStyle: UndefOr[js.Dynamic] = js.undefined)

  override val component = ReactRouterDOM.NavLink
}
