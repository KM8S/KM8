package io.kafkamate
package messages

import io.grpc.stub.{ClientCallStreamObserver, StreamObserver}

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
        println(s"Failed consuming messages: ${throwable.getMessage}")
        onFailure(throwable)
        stop()
      }

      def onCompleted(): Unit = {
        println("Finished consuming messages!")
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
        println("Starting to read the stream...")
        service.consumeMessages(request, newStreamObs(onMessage, onError, onCompleted))
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
