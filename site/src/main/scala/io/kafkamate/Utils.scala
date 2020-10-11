package io.kafkamate

import io.grpc.stub.{ClientCallStreamObserver, StreamObserver}
import scalapb.grpcweb.Metadata

object Utils {

  case class KafkaMateServiceGrpcConsumer(
    service: KafkaMateServiceGrpcWeb.KafkaMateService[Metadata]
  ) {
    private var stream: ClientCallStreamObserver = _

    private def responseObs(onMessage: Message => Unit): StreamObserver[Message] =
      new StreamObserver[Message] {
        def onNext(value: Message): Unit =
          onMessage(value)

        def onError(throwable: Throwable): Unit = {
          println(s"Failed consuming messages: ${throwable.getMessage}")
          stop()
        }

        def onCompleted(): Unit = {
          println("Finished consuming messages!")
          stop()
        }
      }

    def start(request: Request)(onMessage: Message => Unit): Unit =
      stream =
        if (stream == null) {
          println("Starting to read the stream...")
          service.consumeMessages(request, responseObs(onMessage))
        } else {
          println("Stream already started!")
          stream
        }

    def stop(): Unit =
      if (stream == null) println("Stream already stopped!")
      else {
        stream.cancel()
        stream = null
        println("Stream canceled!")
      }
  }

}
