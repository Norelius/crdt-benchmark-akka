package norelius.akka

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import norelius.akka.SimpleCounter.Increment

object Client {

  trait Command

  final case class SendUpdates(amount: Int) extends Command

  def apply(replica: ActorRef[SimpleCounter.Command]): Behavior[Client.SendUpdates] = {
    generator(replica)
  }

  private def generator(replica: ActorRef[SimpleCounter.Command]): Behavior[Client.SendUpdates] =
    Behaviors.receive { (context, message) =>
      context.log.info("Client {} sending {} updates to {}",
        context.self.path.name, message.amount, replica.path.name)
      for (n <- 1 to message.amount) {
        replica ! Increment(n)
      }
      context.log.info("Client {} finished sending all update messages", context.self.path.name)
      Behaviors.stopped
    }
}
