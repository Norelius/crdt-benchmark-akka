package norelius.akka

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import norelius.akka.SimpleCounter.Increment

import scala.util.Random

object MessageGenerator {
  // Fixed seed for assigning replicas to messages.
  final private val seed = 386
  trait Command
  final case class SendMessages(amount: Int) extends Command

  def apply(replicas: Array[ActorRef[SimpleCounter.Command]]): Behavior[MessageGenerator.SendMessages] = {
    generator(replicas)
  }

  private def generator(replicas: Array[ActorRef[SimpleCounter.Command]]): Behavior[MessageGenerator.SendMessages] =
    Behaviors.receive { (context, message) =>
      context.log.info("Sending {} updates spread between {} replicas", message.amount, replicas.length)
      val rand = new Random(seed)
      for( n <- 1 to message.amount) {
        replicas(rand.nextInt(replicas.length)) ! Increment(n)
      }
      context.log.info("All update messages finished sending")
      Behaviors.stopped
      }
}
