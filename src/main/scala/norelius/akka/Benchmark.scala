package norelius.akka

import akka.actor.typed.ActorSystem
import norelius.akka.ReplicaManager.{Setup, Start}
import norelius.akka.SimpleCounter.{Config, Ratio}

import scala.concurrent.duration.DurationInt

object Benchmark extends App {
  // The number of replicas in the network.
  val numberOfReplicas = 9
  // The average percentage of replicas in the network that a replica should
  // send it's state to when it send the state.
  val sendBehavior = Ratio(0.5)
  // How often a replica should share it's state with other replicas.
  val sendFrequency = 500.milliseconds
  // If replicas should shut down after a certain number of queries.
  val finiteQueries = true
  // The number of queries until a replica shuts down.
  val maxQueries: Int = 2000000 / numberOfReplicas
  // The number of clients sending updates per replica.
  val clientsPerReplica = 1
  // The number of update messages per client per replica that should be sent.
  val numberOfUpdates = maxQueries / clientsPerReplica

  val replicaManager: ActorSystem[ReplicaManager.Command] = ActorSystem(ReplicaManager(), "ReplicaManager")
  replicaManager ! Setup(numberOfReplicas,
    Config(sendBehavior, sendFrequency, finiteQueries, maxQueries),
    clientsPerReplica)
  Thread.sleep(50) // Make sure all replicas and client are up and running.
  replicaManager ! Start(numberOfUpdates)
}
