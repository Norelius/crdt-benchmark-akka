package norelius.akka

import akka.actor.typed.ActorSystem
import norelius.akka.ReplicaManager.{Setup, Start}
import norelius.akka.SimpleCounter.{Ratio}

import scala.concurrent.duration.DurationInt

object Benchmark extends App {
  // The number of replicas in the network.
  val numberOfReplicas = 3
  // The average percentage of replicas in the network that a replica should
  // send it's state to when it send the state.
  val sendBehavior = Ratio(0.5)
  // How often a replica should share it's state with other replicas.
  val sendFrequency = 500.milliseconds
  // The number of update messages that should be sent. The messages are
  // uniformly distributed bet
  // ween the replicas.
  val numberOfUpdates = 10 //0000

  val replicaManager: ActorSystem[ReplicaManager.Command] = ActorSystem(ReplicaManager(), "ReplicaManager")
  replicaManager ! Setup(numberOfReplicas, sendBehavior, sendFrequency)
  replicaManager ! Start(numberOfUpdates)
  Thread.sleep(30000)
  replicaManager.terminate()
}
