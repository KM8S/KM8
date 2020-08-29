package io.kafkamate

import io.grpc.{Channel, ManagedChannel, MethodDescriptor, Status, StatusRuntimeException}
import io.grpc.stub.StreamObserver
import slinky.core._
import slinky.core.facade.Hooks._
import slinky.web.html._
import scalapb.grpc.{Channels, ClientCalls}
import scalapb.grpcweb.Metadata
import scalapb.grpcweb.native.{ClientReadableStream, ErrorInfo, StatusInfo}

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

@JSImport("resources/App.css", JSImport.Default)
@js.native
object AppCSS extends js.Object

@JSImport("resources/logo.svg", JSImport.Default)
@js.native
object ReactLogo extends js.Object

object KafkaMateApp {
  private val css = AppCSS

  case class Props(name: String)

  private val grpcChannel = Channels.grpcwebChannel("http://localhost:8081")
  private val kafkaGrpcClient = KafkaMateServiceGrpcWeb.stub(grpcChannel)

  case class Consumer(channel: ManagedChannel) {
    private var stream: ClientReadableStream = _

    private def responseObs: StreamObserver[Message] = new StreamObserver[Message] {
      def onNext(value: Message): Unit =
        println(s"Message consumed: $value")

      def onError(throwable: Throwable): Unit = {
        println(s"Failed consuming messages: ${throwable.getMessage}")
        stop()
      }

      def onCompleted(): Unit = {
        println("Finished consuming messages!")
        stop()
      }
    }

    private def asyncServerStreamingCall[ReqT, RespT](
      channel: Channel,
      method: MethodDescriptor[ReqT, RespT],
      metadata: Metadata = Metadata.empty,
      request: ReqT,
      responseObserver: StreamObserver[RespT]
    ): ClientReadableStream =
      channel.client
        .rpcCall(
          channel.baseUrl + "/" + method.fullName,
          request,
          metadata,
          method.methodInfo
        )
        .on("data", { res: RespT => responseObserver.onNext(res) })
        .on(
          "status",
          { statusInfo: StatusInfo =>
            if (statusInfo.code != 0) {
              responseObserver.onError(
                new StatusRuntimeException(Status.fromStatusInfo(statusInfo))
              )
            } else {
              // Once https://github.com/grpc/grpc-web/issues/289 is fixed.
              responseObserver.onCompleted()
            }
          }
        )
        .on(
          "error",
          { errorInfo: ErrorInfo =>
            responseObserver
              .onError(
                new StatusRuntimeException(Status.fromErrorInfo(errorInfo))
              )
          }
        )
        .on("end", { _: Any => responseObserver.onCompleted() })

    def start(): Unit =
      stream =
        if (stream == null) {
          println("Start reading stream!")
          asyncServerStreamingCall(channel, KafkaMateServiceGrpcWeb.METHOD_CONSUME_MESSAGES, Metadata.empty, Request("test", "key", "value"), responseObs)
          //ClientCalls.asyncServerStreamingCall(channel, KafkaMateServiceGrpcWeb.METHOD_CONSUME_MESSAGES, CallOptions.DEFAULT, Metadata.empty, Request("test", "key", "value"), responseObs)
        } else {
          println("Stream already started!")
          stream
        }

    def stop(): Unit =
      if (stream == null) println("Stream already stopped!")
      else {
        println("Stopped reading stream!")
        stream.cancel()
        stream = null
      }
  }

  val component = FunctionalComponent[Props] { case Props(name) =>
    val (state, updateState) = useState(0)

    def produceMessage() =
      kafkaGrpcClient
        .produceMessage(Request("test", "key", "value"))
        .onComplete {
          case Success(v) => updateState(state + 1); println("Message produced: " + v)
          case Failure(e) => updateState(state - 1); println("Error producing message: " + e)
        }

    val consumer = Consumer(grpcChannel)

    div(className := "App")(
      header(className := "App-header")(
        img(src := ReactLogo.asInstanceOf[String], className := "App-logo", alt := "logo"),
        h1(className := "App-title")("Welcome to KafkaMate!")
      ),
      br(),
      button(onClick := { () => produceMessage() })(s"Produce random message!"),
      p(className := "App-intro")(s"Produced $state messages!"),
      br(),
      button(onClick := { () => consumer.start() })(s"Stream data to console!"),
      button(onClick := { () => consumer.stop() })(s"Close stream!")
    )
  }
}
