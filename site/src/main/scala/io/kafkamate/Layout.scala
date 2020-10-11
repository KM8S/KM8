package io.kafkamate

import slinky.core.annotations.react
import slinky.core.facade.Fragment
import slinky.core.facade.ReactElement
import slinky.core.FunctionalComponent
import slinky.reactrouter.Link
import slinky.web.html._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import bridges.reactrouter.{NavLink, ReactRouterDOM}
import MainRouter.Loc

@react object Layout {
  case class Props(content: ReactElement)

  private def createRegularMenuItem(idx: String, label: String, location: String) =
    li(key := idx, className := "nav-item", NavLink(exact = true, to = location)(className := "nav-link", label))

  private def navPath(currentPath: String) =
    nav(
      className := "navbar fixed-top navbar-expand-lg navbar-dark bg-dark",
      Link(to = Loc.home)(
        className := "navbar-brand",
        //img(src := ReactLogo.asInstanceOf[String], className := "App-logo d-inline-block align-top", alt := ""),
        "KafkaMate"
      ),
      button(
        className := "navbar-toggler",
        `type` := "button",
        data - "toggle" := "collapse",
        data - "target" := "#navbarSupportedContent",
        aria - "controls" := "navbarSupportedContent",
        aria - "expanded" := "false",
        aria - "label" := "Toggle navigation",
        span(className := "navbar-toggler-icon")
      ),
      div(
        className := "collapse navbar-collapse",
        id := "navbarSupportedContent",
        ul(
          className := "navbar-nav mr-auto",
          createRegularMenuItem("100", "Brokers", Loc.brokers),
          createRegularMenuItem("200", "Topics", Loc.topics)
        )
      )
    )

  val component = FunctionalComponent[Props] { props =>
    val location = ReactRouterDOM.useLocation()

    Fragment(
      navPath(location.pathname),
      div(className := "container",
        div(className := "main-content mt-5", role := "main", props.content)
      )
    )
  }
}
