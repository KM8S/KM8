package io.kafkamate
package common

import slinky.core.facade.ReactElement
import slinky.web.html._

object Loader {

  private def renderLoader: ReactElement =
    div(className := "d-flex justify-content-center")(
      div(className := "lds-facebook")(div(), div(), div())
    )

  private def renderError(error: String): ReactElement =
    div(className := "d-flex justify-content-center",
      h3(error)
    )

  def render(shouldRefresh: Boolean, errors: Option[String], default: ReactElement): ReactElement =
    if (shouldRefresh) renderLoader
    else errors match {
      case None => default
      case Some(v) => renderError(v)
    }

}
