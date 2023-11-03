package io.kafkamate
package common

import slinky.core.FunctionalComponent
import slinky.core.annotations.react
import slinky.web.html._

@react object NotFound {
  type Props = Unit

  val component = FunctionalComponent[Props] { _ =>
    div(className := "App d-flex justify-content-center")(
      h1("404 Not Found!")
    )
  }
}
