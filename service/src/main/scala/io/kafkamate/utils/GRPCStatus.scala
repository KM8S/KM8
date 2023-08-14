package io.kafkamate
package utils

import io.grpc.Status

object GRPCStatus {

  def fromThrowable(e: Throwable): Status =
    Status.INVALID_ARGUMENT.withDescription(e.getMessage).withCause(e)

}
