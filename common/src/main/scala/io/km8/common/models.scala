package io.km8.common

enum MessageFormat:
  case STRING, PROTOBUF

final case class ConsumeRequest(
  clusterId: String,
  topicName: String,
  maxResults: Long,
  offsetStrategy: String,
  filterKeyword: String,
  messageFormat: MessageFormat)

final case class ProduceRequest(
  clusterId: String,
  topicName: String,
  key: String,
  value: String)

final case class ProduceResponse(status: String)

final case class Message(
  offset: Long,
  partition: Int,
  timestamp: Long,
  key: String,
  value: String)

final case class BrokerRequest(clusterId: String)

final case class BrokerDetails(id: Int, isController: Boolean)

final case class BrokerResponse(brokers: Seq[BrokerDetails])

final case class AddTopicRequest(
  clusterId: String,
  name: String,
  partitions: Int,
  replication: Int,
  cleanupPolicy: String,
  retentionMs: String)

final case class GetTopicsRequest(clusterId: String)

final case class DeleteTopicRequest(clusterId: String, topicName: String)

final case class TopicDetails(
  name: String,
  partitions: Int,
  replication: Int,
  cleanupPolicy: String,
  retentionMs: String,
  size: Long)

final case class TopicResponse(topics: Seq[TopicDetails])

final case class DeleteTopicResponse(name: String)

final case class ClusterDetails(
  id: String,
  name: String,
  kafkaHosts: String,
  schemaRegistryUrl: String)

final case class ClusterResponse(brokers: ClusterDetails)

final case class ClusterRequest()

enum ConsumerGroupInternalState:
  case Unknown, PreparingRebalance, CompletingRebalance, Stable, Dead, Empty

final case class ConsumerGroupInternal(groupId: String, state: ConsumerGroupInternalState)

final case class ConsumerGroupsResponse(groups: List[ConsumerGroupInternal])

final case class TopicPartitionInternal(name: String, partition: Int)

final case class ConsumerGroupOffsetsResponse(offsets: Map[TopicPartitionInternal, Long])
