package io.kafkamate
package common

import scala.scalajs.js

import slinky.core.facade.ReactElement
import slinky.web.html._

object DeleteModal { //todo: try converting it to a react component

  def toggleModal(modalId: String): Any =
    js.eval("$('" + s"#$modalId" + "').modal('toggle')")

  def renderDeleteModal(
    idx: String,
    title: String,
    body: ReactElement,
    error: Option[String],
    onDelete: String => Unit,
    onCancel: String => Unit
  ): ReactElement = {
    val modalId = s"deleteModalNr$idx"
    div(
      button(
        className:= "btn btn-danger fa",
        data-"toggle" := "modal",
        data-"backdrop" := "static",
        data-"keyboard" := "false",
        data-"target" := s"#$modalId")("Delete"),
      div(className := "modal fade", id := modalId, role := "dialog",
        div(className := "modal-dialog modal-dialog-centered", role := "document",
          div(className := "modal-content",
            div(className :="modal-header",
              h5(className := "modal-title")(title)
            ),
            div(className := "modal-body")(
              body,
              error.zipWithIndex.map {
                case (msg, idx) =>
                  div(key := idx.toString, className := "alert alert-danger", role := "alert", msg)
              }
            ),
            div(className := "modal-footer")(
              button(className := "btn btn-secondary", onClick := (() => onCancel(modalId)))("Cancel"),
              button(className := "btn btn-danger", onClick := (() => onDelete(modalId)))("Delete")
            )
          )
        )
      )
    )
  }

}
