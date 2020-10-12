package io.kafkamate

import slinky.core.annotations.react
import slinky.core.FunctionalComponent
import slinky.reactrouter.Route
import slinky.reactrouter.Switch

import scalajs.js

import bridges.PathToRegexp

@react object MainRouter {
  case class Props(appName: String)

  val component = FunctionalComponent[Props] { case Props(appName) =>
    val routerSwitch = Switch(
      Route(exact = true, path = Loc.home, component = ListBrokers.component),
      Route(exact = true, path = Loc.brokers, component = ListBrokers.component),
      Route(exact = true, path = Loc.topics, component = ListTopics.component),
      Route(exact = true, path = Loc.messages, component = ListMessages.component)
    )

    Layout(routerSwitch)
  }
}

object Loc {
  val home           = "/"
  val brokers        = "/brokers"
  val topics         = "/topics"
  val messages       = "/topics/:name(.*)"

  def pathToTopic(name: String): String = {
    val compiled = PathToRegexp.compile(Loc.messages)
    compiled(
      js.Dynamic
        .literal(
          name = name
        )
        .asInstanceOf[PathToRegexp.ToPathData]
    )
  }
}
