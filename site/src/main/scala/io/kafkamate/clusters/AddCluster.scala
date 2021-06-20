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

import common._

@react object AddCluster {
  type Props = Unit

  private val clustersGrpcClient =
    ClustersServiceGrpcWeb.stub(Channels.grpcwebChannel(Config.GRPCHost))

  val component = FunctionalComponent[Props] { _ =>
    val (shouldRedirect, setRedirect)         = useState(false)
    val (shouldMakeRequest, setRequestAction) = useState(false)
    val (clusterName, setClusterName)         = useState("")
    val (hosts, setHosts)                     = useState("")
    val (schemaRegistry, setSchemaRegistry)   = useState("")
    val (errorMsgs, setErrorMsgs)             = useState(List.empty[String])

    def handleClusterName(e: SyntheticEvent[html.Input, Event]): Unit    = setClusterName(e.target.value)
    def handleHosts(e: SyntheticEvent[html.Input, Event]): Unit          = setHosts(e.target.value)
    def handleSchemaRegistry(e: SyntheticEvent[html.Input, Event]): Unit = setSchemaRegistry(e.target.value)

    def handleSubmit(e: SyntheticEvent[html.Form, Event]) = {
      e.preventDefault()
      setRequestAction(true)
    }

    useEffect(
      () =>
        if (shouldMakeRequest)
          clustersGrpcClient
            .addCluster(ClusterDetails("", clusterName, hosts, schemaRegistry))
            .onComplete {
              case Success(_) =>
                setRedirect(true)

              case Failure(e) =>
                setRequestAction(false)
                setErrorMsgs(List(e.getMessage))
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
            span(className := "input-group-text", "name", id := "form-name-label")
          ),
          input(
            `type` := "text",
            className := "form-control",
            placeholder := "just a simple name",
            aria - "label" := "name",
            aria - "describedby" := "form-name-label",
            value := clusterName,
            onChange := (handleClusterName(_))
          )
        ),
        div(
          className := "input-group mb-3",
          div(
            className := "input-group-prepend",
            span(className := "input-group-text", "hosts", id := "form-hosts-label")
          ),
          input(
            `type` := "text",
            className := "form-control",
            placeholder := "comma separated kafka hosts",
            aria - "label" := "hosts",
            aria - "describedby" := "form-hosts-label",
            value := hosts,
            onChange := (handleHosts(_))
          )
        ),
        div(
          className := "input-group mb-3",
          div(
            className := "input-group-prepend",
            span(className := "input-group-text", "schema registry url", id := "form-registry-label")
          ),
          input(
            `type` := "text",
            className := "form-control",
            placeholder := "required only for protobuf decoder",
            aria - "label" := "registry",
            aria - "describedby" := "form-registry-label",
            value := schemaRegistry,
            onChange := (handleSchemaRegistry(_))
          )
        ),
        errorMsgs.zipWithIndex.map { case (msg, idx) =>
          div(key := idx.toString, className := "alert alert-danger", role := "alert", msg)
        },
        button(`type` := "submit", className := "btn btn-secondary", "Add")
      )

    def addCluster() =
      div(
        className := "container w-50 p-4",
        div(
          className := "card",
          div(className := "card-header", "Add cluster"),
          div(
            className := "card-body",
            addClusterForm()
          )
        )
      )

    if (shouldRedirect)
      Redirect(to = Loc.clusters)
    else
      addCluster()
  }
}
