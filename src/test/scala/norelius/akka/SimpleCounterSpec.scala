package norelius.akka

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import norelius.akka.SimpleCounter._
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

class SimpleCounterSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "A OGCounter" must {
    "remove it's own reference when setting replicas" in {
      val managerProbe = createTestProbe[ReplicaManager.ReplicaFinished]
      val underTest = spawn(SimpleCounter(managerProbe.ref, 0, 2, Config(
        sendBehavior = One(),
        sendFrequency = 10.seconds,
        finiteQueries = false)))
      val otherReplica = spawn(SimpleCounter(managerProbe.ref, 0, 2, Config(
        sendBehavior = One(),
        sendFrequency = 10.seconds,
        finiteQueries = false)))
      underTest ! SetReplicas(mid = 0, Set(underTest, otherReplica))
      val replyProbe = createTestProbe[SimpleCounter.RespondReplicas]()
      underTest ! RequestReplicas(4, replyProbe.ref)
      replyProbe.expectMessage(RespondReplicas(4, Set(otherReplica)))
    }

    "shut down according to config" in {
      val managerProbe1 = createTestProbe[ReplicaManager.ReplicaFinished]
      val managerProbe2 = createTestProbe[ReplicaManager.ReplicaFinished]
      val underTest1 = spawn(SimpleCounter(managerProbe1.ref, 0, 2, Config(
        sendBehavior = All(),
        sendFrequency = 10.seconds,
        finiteQueries = false)))
      val underTest2 = spawn(SimpleCounter(managerProbe2.ref, 0, 2, Config(
        sendBehavior = All(),
        sendFrequency = 10.seconds,
        finiteQueries = true,
        maxQueries = 2
      )))
      underTest1 ! SetReplicas(mid = 0, Set(underTest1, underTest2))
      underTest2 ! SetReplicas(mid = 0, Set(underTest1, underTest2))
      for (n <- 0 to 20) underTest1 ! Increment(n)
      managerProbe1.expectNoMessage(3.seconds)
      underTest2 ! Increment(0)
      underTest2 ! Increment(1)
      managerProbe2.expectMessage(ReplicaManager.ReplicaFinished(underTest2))
    }
  }

}
