package io.kafkamate

import slinky.core.annotations.react
import slinky.core.FunctionalComponent
import slinky.reactrouter.Link
import slinky.web.html._

@react object NotFound {
  type Props = Unit

  val component = FunctionalComponent[Props] { _ =>
    div(className := "App")(
      h1("404 Not Found")
    )
  }
}
