package io.km8.common

import zio.*
import zio.stream.*

trait TopicsService {
  def AddTopic(request: AddTopicRequest): Task[TopicDetails]
  def GetTopics(request: GetTopicsRequest): Task[ TopicResponse]
  def DeleteTopic(request: DeleteTopicRequest): Task[DeleteTopicResponse]
}

trait BrokersService {
  def GetBrokers(request:BrokerRequest): Task[BrokerResponse]
}

trait ClustersService {
  def AddCluster(request:ClusterDetails): Task[ClusterDetails]
  def DeleteCluster(request:ClusterDetails): Task[ClusterResponse]
  def GetClusters(request:ClusterRequest): Task[ClusterResponse]
}

trait MessagesService {
  def ProduceMessage(request:ProduceRequest): Task[ProduceResponse];

  def ConsumeMessages(request:ConsumeRequest): ZStream[Any, Throwable,  Message];
}


