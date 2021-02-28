package io.kafkamate
package common

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.scalajs.js

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.core.facade.ReactElement
import slinky.web.html._

@react object DeleteItemModal {
  case class Props(
    idx: String,
    toDeleteItem: String,
    body: ReactElement,
    deleteCall: () => Future[_],
    onSuccessCallback: () => Unit
  )

  case class DeleteState(
    itemName: Option[String] = None,
    modalId: Option[String] = None,
    error: Option[String] = None
  )

  sealed trait DeleteEvent
  case class SetToDeleteEvent(itemName: String, modalId: String) extends DeleteEvent
  case class CancelDeleteEvent(modalId: Option[String])          extends DeleteEvent
  case class SetDeleteErrorEvent(err: String)                    extends DeleteEvent

  private def deleteReducer(state: DeleteState, action: DeleteEvent): DeleteState =
    action match {
      case SetToDeleteEvent(itemName, modalId) => DeleteState(Some(itemName), Some(modalId))
      case CancelDeleteEvent(maybeModalId)     => DeleteState(None, maybeModalId)
      case SetDeleteErrorEvent(err)            => state.copy(error = Some(err))
    }

  val component = FunctionalComponent[Props] { props =>
    val (deleteState, dispatchEvent) = useReducer(deleteReducer, DeleteState())

    useEffect(
      () =>
        deleteState match {
          case DeleteState(None, Some(modalId), _) =>
            toggleModal(modalId)
            dispatchEvent(CancelDeleteEvent(None))
          case DeleteState(Some(topicName), Some(modalId), None) =>
            props
              .deleteCall()
              .onComplete {
                case Success(_) =>
                  toggleModal(modalId)
                  props.onSuccessCallback()
                case Failure(e) =>
                  Util.logMessage(s"Delete item error: ${e.getMessage}")
                  dispatchEvent(SetDeleteErrorEvent(s"Could not delete item $topicName"))
              }
          case _ => ()
        },
      List(deleteState)
    )

    def toggleModal(modalId: String): Any =
      js.eval("$('" + s"#$modalId" + "').modal('toggle')")

    def renderDeleteModal(
      idx: String,
      toDeleteItem: String,
      body: ReactElement
    ): ReactElement = {
      val modalId = s"deleteModalNr$idx"
      div(
        button(
          className := "btn btn-danger fa",
          data - "toggle" := "modal",
          data - "backdrop" := "static",
          data - "keyboard" := "false",
          data - "target" := s"#$modalId"
        )("Delete"),
        div(
          className := "modal fade",
          id := modalId,
          role := "dialog",
          div(
            className := "modal-dialog modal-dialog-centered",
            role := "document",
            div(
              className := "modal-content",
              div(className := "modal-header", h5(className := "modal-title")(toDeleteItem)),
              div(className := "modal-body")(
                body,
                deleteState.error.zipWithIndex.map { case (msg, idx) =>
                  div(key := idx.toString, className := "alert alert-danger", role := "alert", msg)
                }
              ),
              div(className := "modal-footer")(
                button(
                  className := "btn btn-secondary",
                  onClick := (() => dispatchEvent(CancelDeleteEvent(Some(modalId))))
                )("Cancel"),
                button(
                  className := "btn btn-danger",
                  onClick := (() => dispatchEvent(SetToDeleteEvent(toDeleteItem, modalId)))
                )("Delete")
              )
            )
          )
        )
      )
    }

    renderDeleteModal(props.idx, props.toDeleteItem, props.body)
  }
}
