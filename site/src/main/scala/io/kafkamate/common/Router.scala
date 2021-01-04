package io.kafkamate
package common

import slinky.core.annotations.react
import slinky.core.FunctionalComponent
import slinky.reactrouter._

import brokers._
import clusters._
import topics._
import messages._

@react object Router {
  case class Props(appName: String)

  val component = FunctionalComponent[Props] { case Props(appName) =>
    val routerSwitch = Switch(
      Route(exact = true, path = Loc.home, component = ListClusters.component),
      Route(exact = true, path = Loc.clusters, component = ListClusters.component),
      Route(exact = true, path = Loc.addCluster, component = AddCluster.component),
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