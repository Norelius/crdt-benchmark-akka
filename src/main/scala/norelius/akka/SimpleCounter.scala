package norelius.akka

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import norelius.akka.SimpleCounter.Config
import norelius.crdt.GrowOnlyCounter
import upickle.default._

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

object SimpleCounter {
  def apply(manager: ActorRef[ReplicaManager.ReplicaFinished],
            replicaId: Int,
            size: Int,
            config: Config): Behavior[Command] =
    Behaviors.setup(context =>
      Behaviors.withTimers(timers =>
        new SimpleCounter(context, timers, manager, config,
          new GrowOnlyCounter(replicaId, new Array[Int](size)))))

  class SendBehavior()

  final case class One() extends SendBehavior

  final case class Ratio(ratio: Double) extends SendBehavior

  final case class All() extends SendBehavior

  final case class Config(sendBehavior: SendBehavior,
                          sendFrequency: FiniteDuration,
                          finiteQueries: Boolean, // Queries are only updates. Add reads if needed.
                          maxQueries: Long = 0)

  sealed trait Command

  // Update/Increment local counter
  final case class Increment(mid: Int) extends Command

  // Merge: A serialized version of a state to be shared between replicas
  final case class State(mid: Int, payload: String) extends Command

  // The value function
  final case class ReadValue(mid: Int, replyTo: ActorRef[RespondValue]) extends Command

  final case class RespondValue(mid: Int, value: Int) extends Command

  // Periodic message sent according to the send frequency. Checks if state should be sent out.
  final case class Ping() extends Command

  // A set of all all replicas in the network, including the local one.
  final case class SetReplicas(mid: Int, rep: Set[ActorRef[Command]]) extends Command

  // For testing
  final case class RequestReplicas(mid: Int, replyTo: ActorRef[RespondReplicas]) extends Command

  final case class RespondReplicas(mid: Int, rep: Set[ActorRef[Command]]) extends Command

}

class SimpleCounter(
                     context: ActorContext[SimpleCounter.Command],
                     timers: TimerScheduler[SimpleCounter.Command],
                     manager: ActorRef[ReplicaManager.ReplicaFinished],
                     config: Config,
                     state: GrowOnlyCounter)
  extends AbstractBehavior[SimpleCounter.Command](context) {

  import SimpleCounter._

  var replicas: Array[ActorRef[Command]] = Array[ActorRef[Command]]()
  val rand = new Random(state.replica)
  var stateMessageID = 0
  var lastStateSent: Long = 0
  var queriesReceived: Long = 0
  // Log Startup
  context.log.info("{} started", context.self.path.name)

  override def onMessage(msg: Command): Behavior[Command] = {
    // Sending state takes priority over some messages, in those cases we check if it's time to send the state.
    // The timed pings make sure we always send out updates on time.
    msg match {
      case Increment(mid) =>
        sendIfTime
        queriesReceived += 1
        state.update(1)
        // context.log.info("{} performed inc-{}", context.self.path.name, mid)
        // Check if a max number of queries is set and stop behavior if limit is reached.
        if (config.finiteQueries && queriesReceived >= config.maxQueries) {
          context.log.info("{} reached max number of queries {} with last query [inc-{}]",
            context.self.path.name, queriesReceived, mid)
          manager ! ReplicaManager.ReplicaFinished(context.self)
        }
        this
      case State(_, payload) =>
        sendIfTime
        state.merge(read[GrowOnlyCounter](payload))
        // Very chatty logs if enables but allows retracing of all messages.
        // context.log.info("{} performed merge-{}", context.self.path.name, mid)
        this
      case ReadValue(mid, replyTo) =>
        sendIfTime
        replyTo ! RespondValue(mid, state.value)
        // context.log.info("{} performed valueread-{} with value={}", context.self.path.name, mid, state.value)
        this
      case SetReplicas(_, rep) =>
        replicas = (rep - context.self).toArray
        // Start timer to periodically send state to other replicas.
        timers.startTimerWithFixedDelay(Ping(), config.sendFrequency)
        this
      case RequestReplicas(mid, replyTo) =>
        replyTo ! RespondReplicas(mid, replicas.toSet)
        this
      case Ping() =>
        sendIfTime
        this
      case _ =>
        Behaviors.unhandled // Ignore all other messages.
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      timers.cancelAll()
      context.log.info("{} actor stopped", context.self.path.name)
      this
  }

  def sendState(): Unit = {
    stateMessageID += 1
    // context.log.info("{} performed send-{}", context.self.path.name, stateMessageID)
    config.sendBehavior match {
      case One() =>
        replicas(rand.nextInt(replicas.length)) ! State(stateMessageID, write[GrowOnlyCounter](state))
      case Ratio(ratio) =>
        val stateMessage = State(stateMessageID, write[GrowOnlyCounter](state))
        for (rep <- replicas) {
          if (rand.nextDouble() <= ratio) {
            rep ! stateMessage
          }
        }
      case All() =>
        val stateMessage = State(stateMessageID, write[GrowOnlyCounter](state))
        replicas.foreach(r => r ! stateMessage)
    }
  }

  // Check if the state has been sent out within the sendFrequency, if not, send the state.
  def sendIfTime = {
    val now = System.currentTimeMillis()
    if (now - lastStateSent > config.sendFrequency.toMillis) {
      sendState()
    }
  }
}
