package norelius.akka

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object Receiver {

  trait Command

  final case class RespondValue(mid: Int, value: Int) extends Command

  def apply(): Behavior[Receiver.RespondValue] = {
    receiver()
  }

  private def receiver(): Behavior[Receiver.RespondValue] =
    Behaviors.receive { (context, message) =>
      Behaviors.same
    }
}