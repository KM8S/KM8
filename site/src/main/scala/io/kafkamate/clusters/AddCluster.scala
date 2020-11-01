package io.kafkamate
package clusters

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import org.scalajs.dom.{Event, html}
import scalapb.grpc.Channels
import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.reactrouter.Redirect
import slinky.web.html._

@react object AddCluster {
  type Props = Unit

  private val topicsGrpcClient =
    ClustersServiceGrpcWeb.stub(Channels.grpcwebChannel("http://localhost:8081"))

  val component = FunctionalComponent[Props] { _ =>
    val (shouldRedirect, setRedirect) = useState(false)
    val (shouldMakeRequest, setRequestAction) = useState(false)
    val (clusterName, setClusterName) = useState("")
    val (address, setAddress)         = useState("")
    val (errorMsgs, setErrorMsgs)     = useState(List.empty[String])

    def handleClusterName(e: SyntheticEvent[html.Input, Event]): Unit = setClusterName(e.target.value)
    def handleAddress(e: SyntheticEvent[html.Input, Event]): Unit     = setAddress(e.target.value)

    def handleSubmit(e: SyntheticEvent[html.Form, Event]) = {
      e.preventDefault()
      setRequestAction(true)
    }

    useEffect(
      () => {
        if (shouldMakeRequest)
          topicsGrpcClient
            .addCluster(ClusterDetails("", clusterName, address))
            .onComplete {
              case Success(_) =>
                setRedirect(true)

              case Failure(e) =>
                setRequestAction(false)
                setErrorMsgs(List(e.getMessage))
            }
      },
      List(shouldMakeRequest)
    )

    def addClusterForm() =
      form(
        onSubmit := (handleSubmit(_)),
        div(
          className := "input-group mb-3",
          div(
            className := "input-group-prepend",
            span(className := "input-group-text", "name", id := "form-username-label")
          ),
          input(
            `type` := "text",
            className := "form-control",
            placeholder := "Name",
            aria - "label" := "Name",
            aria - "describedby" := "form-username-label",
            value := clusterName,
            onChange := (handleClusterName(_))
          )
        ),
        div(
          className := "input-group mb-3",
          div(
            className := "input-group-prepend",
            span(className := "input-group-text", "address", id := "form-username-label")
          ),
          input(
            `type` := "text",
            className := "form-control",
            placeholder := "Address",
            aria - "label" := "Address",
            aria - "describedby" := "form-password-label",
            value := address,
            onChange := (handleAddress(_))
          )
        ),
        errorMsgs.zipWithIndex.map {
          case (msg, idx) =>
            div(key := idx.toString, className := "alert alert-danger", role := "alert", msg)
        },
        button(`type` := "submit", className := "btn btn-secondary", "Add")
      )

    def addCluster() =
      div(
        className := "card",
        div(className := "card-header", "Add cluster"),
        div(
          className := "card-body",
          addClusterForm()
        )
      )

    if (shouldRedirect)
      Redirect(to = Loc.clusters)
    else
      addCluster()
  }
}
