package norelius.crdt

import org.scalatest.FunSuite
import upickle.default.{read, write}

class TestCrdt extends FunSuite {
  test("GrowOnlyCounter instantiation and update") {
    // TODO: add test using default replica value
    val r = 3
    val g2 = new GrowOnlyCounter(r, new Array[Int](10))
    assert(g2.replica == r)
    assert(g2.value == 0)
    val a = Array(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
    val g3 = new GrowOnlyCounter(0, a)
    assert(g3.c == a)
    assert(g3.value == 45)
  }

  test("GrowOnlyCounter update") {
    val r = 3
    val n = 5
    val g1 = new GrowOnlyCounter(r, new Array[Int](10))
    g1.update(n)
    assert(g1.c(r) == n)
    assert(g1.value == n)
  }

  test("GrowOnlyCounter non-OSD merge") {
    val i1 = 1
    val g1 = new GrowOnlyCounter(i1, Array(0, 5, 0, 0, 0, 0, 0, 0, 0, 0))
    val i2 = 2
    val g2 = new GrowOnlyCounter(i2, Array(0, 0, 100, 0, 0, 0, 0, 0, 0, 0))
    // Merge with lower value at own index.
    g1.merge(g2)
    assert(g1.value == 105)
    assert(g2.value == 100)
    // Merge should be idempotent.
    g1.merge(g2)
    assert(g1.value == 105)
    assert(g2.value == 100)
    // Merge with a value that's higher at own index.
    val i3 = 3
    val g3 = new GrowOnlyCounter(i3, Array(10, 10, 10, 10, 10, 10, 10, 10, 10, 10))
    g1.merge(g3)
    assert(g1.value == 190)
  }

  test("GrowOnlyCounter pickle/unpickle") {
    val sum = 45
    val r = 5
    val a = Array(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
    val g1 = new GrowOnlyCounter(r, a)
    assert(g1.value == sum)
    val s = write(g1)
    assert(s == "\"5 [0,1,2,3,4,5,6,7,8,9]\"")
    val g2 = read[GrowOnlyCounter](s)
    assert(g2.replica == r)
    assert(g2.value == sum)
    assert(g2.c sameElements a)
  }

  test("GrowOnlyCounter OSD merge") {
    var p1 = "\"1 [0,5,0,0,0,0,0,0,0,0]\""
    val p2 = "\"2 [0,0,100,0,0,0,0,0,0,0]\""
    // Merge with lower value at own index.
    p1 = GrowOnlyCounter.mergePickle(p1, p2)
    var g1 = read[GrowOnlyCounter](p1)
    assert(g1.value == 105)
    // Merge should be idempotent.
    g1 = read[GrowOnlyCounter](p1)
    assert(g1.value == 105)
    // Merge with a value that's higher at own index.
    val p3 = "\"3 [10,10,10,10,10,10,10,10,10,10]\""
    p1 = GrowOnlyCounter.mergePickle(p1, p3)
    g1 = read[GrowOnlyCounter](p1)
    assert(g1.value == 190)
  }
}
