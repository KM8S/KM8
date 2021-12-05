package io.km8.common

import zio.*
import zio.stream.*

trait TopicsService {
  def addTopic(request: AddTopicRequest): Task[TopicDetails]
  def getTopics(request: GetTopicsRequest): Task[TopicResponse]
  def deleteTopic(request: DeleteTopicRequest): Task[DeleteTopicResponse]
}

trait BrokersService {
  def getBrokers(request: BrokerRequest): Task[BrokerResponse]
}

trait ClustersService {
  def addCluster(request: ClusterDetails): Task[ClusterDetails]
  def deleteCluster(request: ClusterDetails): Task[ClusterResponse]
  def getClusters(request: ClusterRequest): Task[ClusterResponse]
}

trait MessagesService {
  def produceMessage(request: ProduceRequest): Task[ProduceResponse];

  def consumeMessages(request: ConsumeRequest): ZStream[Any, Throwable, Message];
}
