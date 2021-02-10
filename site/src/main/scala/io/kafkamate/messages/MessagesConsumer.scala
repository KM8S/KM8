package io.kafkamate
package messages

import io.grpc.stub.{ClientCallStreamObserver, StreamObserver}

import common._

case class MessagesConsumer(
  service: MessagesServiceGrpcWeb.MessagesService[_]
) {
  private var stream: ClientCallStreamObserver = _

  private def newStreamObs(
    onMessage: Message => Unit,
    onFailure: Throwable => Unit,
    onTerminated: () => Unit
  ): StreamObserver[Message] =
    new StreamObserver[Message] {
      def onNext(value: Message): Unit =
        onMessage(value)

      def onError(throwable: Throwable): Unit = {
        Util.logMessage(s"Failed consuming messages: ${throwable.getMessage}")
        onFailure(throwable)
        stop()
      }

      def onCompleted(): Unit = {
        Util.logMessage("Finished consuming messages!")
        onTerminated()
        stop()
      }
    }

  def start(request: ConsumeRequest)(
    onMessage: Message => Unit,
    onError: Throwable => Unit,
    onCompleted: () => Unit
  ): Unit =
    stream =
      if (stream == null) {
        Util.logMessage("Starting to read the stream...")
        service.consumeMessages(request, newStreamObs(onMessage, onError, onCompleted))
      } else {
        Util.logMessage("Stream already started!")
        stream
      }

  def stop(): Unit =
    if (stream == null) Util.logMessage("Stream already stopped!")
    else {
      stream.cancel()
      stream = null
      Util.logMessage("Stream canceled!")
    }
}
