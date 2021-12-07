package io.km8.common

enum MessageFormat:
  case STRING, PROTOBUF

case class ConsumeRequest(
  clusterId: String,
  topicName: String,
  maxResults: Long,
  offsetStrategy: String,
  filterKeyword: String,
  messageFormat: MessageFormat)

case class ProduceRequest(
  clusterId: String,
  topicName: String,
  key: String,
  value: String)

case class ProduceResponse(status: String)

case class Message(
  offset: Long,
  partition: Int,
  timestamp: Long,
  key: String,
  value: String)

case class BrokerRequest(clusterId: String)

case class BrokerDetails(id: Int, isController: Boolean)

case class BrokerResponse(brokers: Seq[BrokerDetails])

case class AddTopicRequest(
  clusterId: String,
  name: String,
  partitions: Int,
  replication: Int,
  cleanupPolicy: String,
  retentionMs: String)

case class GetTopicsRequest(clusterId: String)

case class DeleteTopicRequest(clusterId: String, topicName: String)

case class TopicDetails(
  name: String,
  partitions: Int,
  replication: Int,
  cleanupPolicy: String,
  retentionMs: String,
  size: Long)

case class TopicResponse(topics: Seq[TopicDetails])

case class DeleteTopicResponse(name: String)

case class ClusterDetails(
  id: String,
  name: String,
  kafkaHosts: String,
  schemaRegistryUrl: String)

case class ClusterResponse(brokers: ClusterDetails)

case class ClusterRequest()
