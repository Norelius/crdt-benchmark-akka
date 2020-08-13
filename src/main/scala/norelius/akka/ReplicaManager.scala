package norelius.akka

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import norelius.akka.Client.SendUpdates
import norelius.akka.SimpleCounter.Config

object ReplicaManager {
  def apply(): Behavior[Command] =
    Behaviors.setup(context => new ReplicaManager(context))

  sealed trait Command

  // TODO: Set what type of merging should be done.
  final case class Setup(numberOfReplicas: Int, config: Config, clientsPerReplica: Int) extends Command

  final case class Start(numberOfMessages: Int) extends Command

  final case class ReplicaFinished(replica: ActorRef[SimpleCounter.Command]) extends Command

}

class ReplicaManager(context: ActorContext[ReplicaManager.Command])
  extends AbstractBehavior[ReplicaManager.Command](context) {

  import ReplicaManager._

  val replicas = scala.collection.mutable.SortedSet[ActorRef[SimpleCounter.Command]]()
  val clients = scala.collection.mutable.SortedSet[ActorRef[Client.SendUpdates]]()
  val terminatedReplicas = scala.collection.mutable.SortedSet[ActorRef[SimpleCounter.Command]]()

  override def onMessage(msg: ReplicaManager.Command): Behavior[ReplicaManager.Command] =
    msg match {
      case Setup(numberOfReplicas, config, clientsPerReplica) =>
        for (n <- 0 to numberOfReplicas - 1) {
          val replica = context.spawn(
            SimpleCounter(context.self.narrow[ReplicaFinished], n, numberOfReplicas, config), "Replica-" + n)
          replicas += replica
          for (m <- 0 to clientsPerReplica - 1) {
            clients += context.spawn(Client(replica), "Client-%d-%d".format(n, m))
          }
        }
        val m = SimpleCounter.SetReplicas(1, replicas.toSet)
        replicas.foreach(r => r ! m)
        context.log.info("Finished setting up {} replicas and {} clients",
          numberOfReplicas, numberOfReplicas * clientsPerReplica)
        this
      case Start(numberOfMessages) =>
        clients.foreach(c => c ! SendUpdates(numberOfMessages))
        this
      case ReplicaFinished(ref) =>
        terminatedReplicas += ref
        if (terminatedReplicas.size == replicas.size) {
          Behaviors.stopped
        } else {
          this
        }
    }

  override def onSignal: PartialFunction[Signal, Behavior[ReplicaManager.Command]] = {
    case PostStop =>
      context.log.info("ReplicaManager actor stopped", context.self.path.name)
      this
  }
}
