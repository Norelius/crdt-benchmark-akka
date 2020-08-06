package norelius.akka

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import norelius.akka.SimpleCounter.{Command, Increment}

import scala.util.Random

object MessageGenerator {
  final private val seed = 386
  trait Command
  final case class SendMessages() extends Command

  def apply(amount: Int, replicas: Array[ActorRef[SimpleCounter.Command]]): Behavior[MessageGenerator.SendMessages] = {
    generator(amount, replicas)
  }

  private def generator(max: Int, replicas: Array[ActorRef[SimpleCounter.Command]]): Behavior[MessageGenerator.SendMessages] =
    Behaviors.receive { (context, message) =>
      context.log.info("Sending {} updates spread between {} replicas", max, replicas.length)
      val rand = new Random(seed)
      for( n <- 1 to max) {
        replicas(rand.nextInt(replicas.length)) ! Increment(n)
      }
      Behaviors.stopped
      }
}
