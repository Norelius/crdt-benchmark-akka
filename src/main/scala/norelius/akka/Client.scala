package norelius.akka

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import norelius.akka.SimpleCounter.{Increment, ReadValue}

import scala.util.Random

object Client {

  trait Command

  final case class SendUpdates(amount: Int, readRatio: Double) extends Command

  final case class RespondValue(mid: Int, value: Int) extends Command

  final case class Acknowledge(mid: Int) extends Command

  def apply(replica: ActorRef[SimpleCounter.Command]): Behavior[Client.Command] = {
    Behaviors.setup(context => new Client(context, replica))
  }
}

class Client(context: ActorContext[Client.Command],
             replica: ActorRef[SimpleCounter.Command])
  extends AbstractBehavior[Client.Command](context) {

  import Client._

  var nextMid = 1
  var amount = 0
  var readRatio = 0.0
  val rand = new Random(System.currentTimeMillis())

  override def onMessage(msg: Client.Command): Behavior[Client.Command] = {
    msg match {
      case Acknowledge(_) => sendOrStop()
      case RespondValue(_, _) => sendOrStop()
      case SendUpdates(a: Int, r: Double) =>
        amount = a
        readRatio = r
        context.log.info("Start time: {}", System.currentTimeMillis())
        context.log.info("Client {} sending {} queries to {}",
          context.self.path.name, amount, replica.path.name)
        sendQuery()
        this
      case _ => Behaviors.unhandled
    }
  }

  def sendQuery(): Unit = {
    if (rand.nextDouble() >= readRatio) {
      replica ! Increment(nextMid, context.self.ref)
      nextMid = nextMid + 1
    } else {
      replica ! ReadValue(nextMid, context.self.ref)
      nextMid = nextMid + 1
    }
  }

  def sendOrStop(): Behavior[Client.Command] = {
    if (nextMid <= amount) {
      sendQuery()
      this
    } else {
      context.log.info("Client {} finished sending all queries messages", context.self.path.name, System.currentTimeMillis())
      Behaviors.stopped
    }
  }
}