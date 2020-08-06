package norelius.akka

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import norelius.akka.MessageGenerator.SendMessages
import norelius.akka.SimpleCounter._
import norelius.crdt.GrowOnlyCounter
import org.scalatest.wordspec.AnyWordSpecLike
import upickle.default.write
import scala.concurrent.duration._

class SimpleCounterSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "A OGCounter" must {
    "remove it's own reference when setting replicas" in {
      val underTest = spawn(SimpleCounter(0, 2, 1))
      val otherReplica = spawn(SimpleCounter(1, 2, 1))
      underTest ! SetReplicas(0, Set(underTest, otherReplica))
      val replyProbe = createTestProbe[SimpleCounter.RespondReplicas]()
      underTest ! RequestReplicas(4, replyProbe.ref)
      replyProbe.expectMessage(RespondReplicas(4, Set(otherReplica)))
    }

    "send it's state to other replicas according to the ratio" in {
      // Send to all replicas
      val underTest = spawn(SimpleCounter(0, 3, 1.0))
      val replyProbe1 = createTestProbe[SimpleCounter.Command]()
      val replyProbe2 = createTestProbe[SimpleCounter.Command]()
      underTest ! SetReplicas(0, Set(underTest, replyProbe1.ref, replyProbe2.ref))
      underTest ! Increment(4)
      val state = write[GrowOnlyCounter](new GrowOnlyCounter(0, Array(1, 0, 0)))
      replyProbe1.expectMessage(SendState(4, state))
      replyProbe2.expectMessage(SendState(4, state))

      // Send to no replicas
      val underTestNoGossip = spawn(SimpleCounter(0, 3, -1))
      underTestNoGossip ! SetReplicas(0, Set(underTestNoGossip, replyProbe1.ref, replyProbe2.ref))
      underTestNoGossip ! Increment(4)
      replyProbe1.expectNoMessage(3.seconds)
      replyProbe2.expectNoMessage(3.seconds)
    }
  }

}
