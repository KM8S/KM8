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

  def render(shouldRefresh: Boolean, loadingError: Option[String], loadingSuccess: ReactElement): ReactElement =
    if (shouldRefresh) renderLoader
    else loadingError match {
      case None => loadingSuccess
      case Some(v) => renderError(v)
    }

}
