package norelius.akka

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import norelius.akka.MessageGenerator.SendMessages

object ReplicaManager {
  def apply(): Behavior[Command] =
    Behaviors.setup(context => new ReplicaManager(context))

  sealed trait Command
  // TODO: Set what type of merging should be done.
  final case class Setup(numberOfReplicas: Int, gossipRatio: Double) extends Command
  final case class Start(numberOfMessages: Int) extends Command
}

class ReplicaManager(context: ActorContext[ReplicaManager.Command])
  extends AbstractBehavior[ReplicaManager.Command](context) {
  import ReplicaManager._

  val replicas = scala.collection.mutable.SortedSet[ActorRef[SimpleCounter.Command]]()

  override def onMessage(msg: ReplicaManager.Command): Behavior[ReplicaManager.Command] =
    msg match {
      case Setup(numberOfReplicas, gossipRatio) =>
        for( n <- 0 to numberOfReplicas -1) {
          replicas += context.spawn(SimpleCounter(n, numberOfReplicas, gossipRatio), "Replica-" + n)
        }
        val m = SimpleCounter.SetReplicas(1, replicas.toSet)
        replicas.foreach(r => r ! m)
        context.log.info("Finished setting up {} replicas", numberOfReplicas)
        this
      case Start(numberOfMessages) =>
        val generator = context.spawn(MessageGenerator(replicas.toArray), "MessageGenerator")
        generator ! SendMessages(numberOfMessages)
        this
    }
}
