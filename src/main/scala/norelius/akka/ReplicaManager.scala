package norelius.akka

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import norelius.akka.Client.SendUpdates
import norelius.akka.Serializer.Serializer
import norelius.akka.SimpleCounter.Config
import norelius.crdt._
import upickle.default.{read, write}

object Serializer extends Enumeration {
  type Serializer = Value
  val Java, Pickle = Value
}

object ReplicaManager {
  def apply(): Behavior[Command] =
    Behaviors.setup(context => new ReplicaManager(context))

  sealed trait Command

  // TODO: Set what type of merging should be done.
  final case class Setup(numberOfReplicas: Int,
                         storedSerialized: Boolean,
                         osdMerge: Boolean,
                         serializer: Serializer,
                         config: Config,
                         clientsPerReplica: Int) extends Command

  final case class Start(numberOfMessages: Int, readRatio: Double) extends Command

  final case class ReplicaFinished(replica: ActorRef[SimpleCounter.Command]) extends Command

}

class ReplicaManager(context: ActorContext[ReplicaManager.Command])
  extends AbstractBehavior[ReplicaManager.Command](context) {

  import ReplicaManager._

  private val replicas = scala.collection.mutable.SortedSet[ActorRef[SimpleCounter.Command]]()
  private val clients = scala.collection.mutable.SortedSet[ActorRef[Client.SendUpdates]]()
  private val terminatedReplicas = scala.collection.mutable.SortedSet[ActorRef[SimpleCounter.Command]]()

  override def onMessage(msg: ReplicaManager.Command): Behavior[ReplicaManager.Command] =
    msg match {
      case Setup(numberOfReplicas, storedSerialized, osdMerge, serializer, config, clientsPerReplica) =>
        val counterConstructor = makeCounterConstructor(storedSerialized, osdMerge, serializer)
        for (n <- 0 to numberOfReplicas - 1) {
          val replica = context.spawn(
            SimpleCounter(context.self.narrow[ReplicaFinished], n, numberOfReplicas,
              counterConstructor, config), "Replica-" + n)
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
      case Start(numberOfMessages, readRatio) =>
        clients.foreach(c => c ! SendUpdates(numberOfMessages, readRatio))
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

  def makeCounterConstructor(storedSerialized: Boolean, osdMerge: Boolean, serializer: Serializer):
  GrowOnlyCounter => Counter = {
    val serializeFunctions = serializerFunctions(serializer)
    if (!storedSerialized) {
      return (c: GrowOnlyCounter) => new deserNonOsdCounter(c, serializeFunctions._1, serializeFunctions._2)
    } else {
      if (osdMerge) {
        return (c: GrowOnlyCounter) => new serOsdCounter(c, serializeFunctions._1, serializeFunctions._2)
      } else {
        return (c: GrowOnlyCounter) => new serNonOsdCounter(c, serializeFunctions._1, serializeFunctions._2)
      }
    }
    context.log.error("Not allowed Counter options")
    throw new IllegalArgumentException("Not allowed Counter options")
  }

  def serializerFunctions(serializer: Serializer): (GrowOnlyCounter => String, String => GrowOnlyCounter) =
    serializer match {
      case Serializer.Java => (
        Serialization.serialize,
        (s: String) => Serialization.deserialize(s).asInstanceOf[GrowOnlyCounter]
      )
      case Serializer.Pickle => (
        (c: GrowOnlyCounter) => write[GrowOnlyCounter](c),
        (s: String) => read[GrowOnlyCounter](s)
      )
    }
}
