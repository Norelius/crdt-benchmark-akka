package norelius.akka

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import norelius.akka.Receiver.RespondValue
import norelius.akka.SimpleCounter.{Increment, ReadValue}

import scala.util.Random

object Client {

  trait Command

  final case class SendUpdates(amount: Int, readRatio: Double) extends Command

  def apply(replica: ActorRef[SimpleCounter.Command], receiver: ActorRef[Receiver.RespondValue]): Behavior[Client.SendUpdates] = {
    generator(replica, receiver)
  }

  private def generator(replica: ActorRef[SimpleCounter.Command], receiver: ActorRef[RespondValue]):
  Behavior[Client.SendUpdates] =
    Behaviors.receive { (context, message) =>
      context.log.info("Start time: {}", System.currentTimeMillis())
      context.log.info("Client {} sending {} queries to {}",
        context.self.path.name, message.amount, replica.path.name)

      val rand = new Random(System.currentTimeMillis())
      for (n <- 1 to message.amount) {
        if (rand.nextDouble() >= message.readRatio) {
          replica ! Increment(n)
        } else {
          replica ! ReadValue(n, receiver)
        }
      }
      context.log.info("Client {} finished sending all queries messages", context.self.path.name, System.currentTimeMillis())
      Behaviors.stopped
    }
}
