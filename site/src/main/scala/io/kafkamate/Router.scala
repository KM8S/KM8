package io.kafkamate

import scalajs.js

import slinky.core.annotations.react
import slinky.core.FunctionalComponent
import slinky.reactrouter._

import bridges.PathToRegexp
import brokers._
import clusters._
import topics._
import messages._

@react object Router {
  case class Props(appName: String)

  val component = FunctionalComponent[Props] { case Props(appName) =>
    val routerSwitch = Switch(
      Route(exact = true, path = Loc.home, component = ListClusters.component),
      Route(exact = true, path = Loc.addCluster, component = AddCluster.component),
      Route(exact = true, path = Loc.clusters, component = ListClusters.component),
      Route(exact = true, path = Loc.brokers, component = ListBrokers.component),
      Route(exact = true, path = Loc.topics, component = ListTopics.component),
      Route(exact = true, path = Loc.addTopic, component = AddTopic.component),
      Route(exact = true, path = Loc.addMessage, component = ProduceMessage.component),
      Route(exact = true, path = Loc.listMessages, component = ListMessages.component),
      Route(exact = false, path = "", component = NotFound.component)
    )

    Layout(routerSwitch)
  }
}

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
    fromTopic(clusterId, topicName)(Loc.listMessages)

  def fromTopicAdd(clusterId: String, topicName: String): String =
    fromTopic(clusterId, topicName)(Loc.addMessage)
}
