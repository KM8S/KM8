package io.kafkamate
package utils

import io.grpc.Status

object GRPCStatus {

  def fromThrowable(e: Throwable): Status =
    Status.INTERNAL.withDescription(e.getMessage).withCause(e)

}
