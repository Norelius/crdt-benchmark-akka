
package norelius.akka

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import norelius.akka.Client.SendUpdates
import norelius.akka.SimpleCounter.Increment
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

class ClientSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "A Client" must {
    "send the right number of messages" in {
      val replyProbe = createTestProbe[SimpleCounter.Command]()
      val underTest = spawn(Client(replyProbe.ref))
      underTest ! SendUpdates(2)
      replyProbe.expectMessage(Increment(1))
      replyProbe.expectMessage(Increment(2))
      replyProbe.expectNoMessage(3.seconds)
    }
  }
}