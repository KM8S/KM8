package io.kafkamate
package common

import scalajs.js

import bridges.PathToRegexp

object Loc {
  val clusterIdKey = "clusterId"
  val topicNameKey = "topicName"

  val home           =  "/"
  val addCluster     =  "/add-cluster"
  val clusters       =  "/clusters"
  val brokers        = s"/clusters/:$clusterIdKey(.*)/brokers"
  val topics         = s"/clusters/:$clusterIdKey(.*)/topics"
  val addTopic       = s"/clusters/:$clusterIdKey(.*)/topics/new-topic"
  val listMessages   = s"/clusters/:$clusterIdKey(.*)/topics/consume/:$topicNameKey(.*)"
  val addMessage     = s"/clusters/:$clusterIdKey(.*)/topics/produce/:$topicNameKey(.*)"

  def fromLocation(clusterId: String, location: String): String = {
    val fromPathData = PathToRegexp.compile(location)
    fromPathData(
      js.Dynamic
        .literal(
          clusterId = clusterId
        )
        .asInstanceOf[PathToRegexp.ToPathData]
    )
  }

  private def fromTopic(clusterId: String, topicName: String)(location: String): String = {
    val fromPathData = PathToRegexp.compile(location)
    fromPathData(
      js.Dynamic
        .literal(
          clusterId = clusterId,
          topicName = topicName
        )
        .asInstanceOf[PathToRegexp.ToPathData]
    )
  }

  def fromTopicList(clusterId: String, topicName: String): String =
    fromTopic(clusterId, topicName)(listMessages)

  def fromTopicAdd(clusterId: String, topicName: String): String =
    fromTopic(clusterId, topicName)(addMessage)
}