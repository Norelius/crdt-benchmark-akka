package norelius.akka

import akka.actor.typed.ActorSystem
import norelius.akka.ReplicaManager.{Setup, Start}

object Benchmark extends App {
  // The number of replicas in the network.
  val numberOfReplicas = 9
  // The average percentage of replicas in the network that a replica should
  // send it's state to when it send the state.
  val gossipRatio = 0.5
  // The number of update messages that should be sent. The messages are
  // uniformly distributed between the replicas.
  val numberOfUpdates = 100000

  val replicaManager: ActorSystem[ReplicaManager.Command] = ActorSystem(ReplicaManager(), "ReplicaManager")
  replicaManager ! Setup(numberOfReplicas, gossipRatio)
  replicaManager ! Start(numberOfUpdates)
}
