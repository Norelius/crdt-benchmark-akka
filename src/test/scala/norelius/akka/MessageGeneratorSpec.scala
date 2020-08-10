package norelius.akka

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import norelius.akka.MessageGenerator.SendMessages
import norelius.akka.SimpleCounter.{Increment}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

class MessageGeneratorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "A MessageGenerator" must {
    "send the right number of messages" in {
      val replyProbe = createTestProbe[SimpleCounter.Command]()
      val replyList = List(replyProbe.ref)
      val underTest = spawn(MessageGenerator(replyList.toArray))
      underTest ! SendMessages(2)
      replyProbe.expectMessage(Increment(1))
      replyProbe.expectMessage(Increment(2))
      replyProbe.expectNoMessage(3.seconds)
    }
  }
}
