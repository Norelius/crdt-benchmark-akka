package norelius.akka

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, Behaviors}
import norelius.crdt.GrowOnlyCounter
import upickle.default._

import scala.util.Random

object SimpleCounter {
  def apply(replicaId: Int, size: Int, gossipRatio: Double): Behavior[Command] =
    Behaviors.setup(context => new SimpleCounter(context,
      new GrowOnlyCounter(replicaId, new Array[Int](size)), gossipRatio))

  sealed trait Command

  // Update/Increment local counter
  final case class Increment(mid: Int) extends Command
  // Merge: A serialized version of a state to be shared between replicas
  final case class SendState(mid: Int, payload: String) extends Command

  // The value function
  final case class ReadValue(mid: Int, replyTo: ActorRef[RespondValue]) extends Command
  final case class RespondValue(mid: Int, value: Int) extends Command

  // A set of all all replicas in the network, including the local one.
  final case class SetReplicas(mid: Int, rep: Set[ActorRef[Command]]) extends Command
  // For testing
  final case class RequestReplicas(mid: Int, replyTo: ActorRef[RespondReplicas]) extends Command
  final case class RespondReplicas(mid: Int, rep: Set[ActorRef[Command]]) extends Command
}

class SimpleCounter(context: ActorContext[SimpleCounter.Command], state: GrowOnlyCounter, gossipRatio: Double)
  extends AbstractBehavior[SimpleCounter.Command](context) {
  import SimpleCounter._

  var replicas: scala.collection.immutable.Set[ActorRef[Command]] = Set.empty
  val rand = new Random(state.replica)


  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case Increment(mid) =>
        // Currently the state is sent out after each update. Here is where we
        // could check how long it's been since we last sent out the state,
        // and only send it if the time limit has been reached. Additionally
        // we could send intermittent pings to make sure state is sent even
        // if no further updates are performed.
        state.update(1)
        for (rep <- replicas) {
          if (rand.nextDouble() <= gossipRatio) {
            rep ! SendState(mid, write[GrowOnlyCounter](state))
          }
        }
        context.log.info("{} performed inc-{}", context.self.path.name, mid)
        this
      case SendState(_, payload) =>
        state.merge(read[GrowOnlyCounter](payload))
        // Very chatty logs if enables but allows retracing of all messages.
        // context.log.info("{} performed merge-{}", context.self.path.name, mid)
        this
      case ReadValue(mid, replyTo) =>
        replyTo ! RespondValue(mid, state.value)
        context.log.info("{} performed valueread-{} with with value={}", context.self.path.name, mid, state.value)
        this
      case SetReplicas(_, rep) =>
        replicas  = rep - context.self
        this
      case RequestReplicas(mid, replyTo) =>
        replyTo ! RespondReplicas(mid, replicas)
        this
      case _ => this // Ignore all other messages.
    }
  }
}
