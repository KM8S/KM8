package io.kafkamate

import slinky.core.annotations.react
import slinky.core.FunctionalComponent
import slinky.reactrouter.Route
import slinky.reactrouter.Switch

import scalajs.js

import bridges.PathToRegexp

@react object MainRouter {
  case class Props(appName: String)

  val component = FunctionalComponent[Props] { case Props(appName) =>
    val routerSwitch = Switch(
      Route(exact = true, path = Loc.home, component = ListClusters.component),
      Route(exact = true, path = Loc.clusters, component = ListClusters.component),
      Route(exact = true, path = Loc.brokers, component = ListBrokers.component),
      Route(exact = true, path = Loc.topics, component = ListTopics.component),
      Route(exact = true, path = Loc.messages, component = ListMessages.component)
    )

    Layout(routerSwitch)
  }
}

object Loc {
  val clusterIdKey = "clusterId"
  val topicNameKey = "topicName"

  val home           =  "/"
  val clusters       =  "/clusters"
  val brokers        = s"/clusters/:$clusterIdKey(.*)/brokers"
  val topics         = s"/clusters/:$clusterIdKey(.*)/topics"
  val messages       = s"/clusters/:$clusterIdKey(.*)/topics/:$topicNameKey(.*)"

  def clustersPath(clusterId: String)(location: String): String = {
    val fromPathData = PathToRegexp.compile(location)
    fromPathData(
      js.Dynamic
        .literal(
          clusterId = clusterId
        )
        .asInstanceOf[PathToRegexp.ToPathData]
    )
  }

  def messagesPath(clusterId: String, topicName: String): String = {
    val fromPathData = PathToRegexp.compile(Loc.messages)
    fromPathData(
      js.Dynamic
        .literal(
          clusterId = clusterId,
          topicName = topicName
        )
        .asInstanceOf[PathToRegexp.ToPathData]
    )
  }
}
