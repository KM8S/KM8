package io.km8.models

case class AddTopic(
  clusterId: String,
  name: String,
  partitions: Int,
  replication: Int,
  cleanupPolicy: String,
  retentionMs: String
)

case class DeleteTopic(
  clusterId: String,
  topicName: String
)

case class BrokerDetails(nodeId: Int, isController: Boolean)

case class TopicDetails(
  name: String,
  partitions: Int,
  replication: Int,
  cleanupPolicy: String,
  retentionMs: String,
  size: Long
)
