package norelius.crdt

sealed trait Base[T, V] {
  def merge(rhs: T): Unit

  def value: V
}

trait GCounter[T] extends Base[T, Int] {
  def update(n: Int): Unit
}

// The default -1 means it's an unpacked array that doesn't have an index, maybe
@SerialVersionUID(123L)
class GrowOnlyCounter(val replica: Int = -1, var c: Array[Int]) extends GCounter[GrowOnlyCounter] with Serializable {
  override def update(n: Int): Unit = {
    c(replica) += n
  }

  override def merge(rhs: GrowOnlyCounter): Unit = {
    for (i <- 0 to (c.length - 1)) {
      if (rhs.c(i) > c(i)) {
        c(i) = rhs.c(i)
      }
    }
  }

  override def value: Int = {
    var sum = 0
    c.foreach(sum += _)
    sum
  }

  override def toString: String = "{" + replica + " " + c.toIndexedSeq.toString() + "}"
}

object GrowOnlyCounter {
  implicit val rw = upickle.default.readwriter[String].bimap[GrowOnlyCounter](
    x => x.replica + " " + upickle.default.write(x.c),
    str => {
      val Array(replica, c) = str.split(" ", 2)
      new GrowOnlyCounter(replica.toInt, upickle.default.read[Array[Int]](c))
    }
  )

  def mergePickle(lhs: String, rhs: String): String = {
    // TODO: Make better string merge method.
    // Sanitize input. This might be super inefficient.
    val Array(replica, lc) = lhs.split(" ", 2)
    val Array(_, rc) = rhs.split(" ", 2)
    val a = splitCounter(lc)
    val b = splitCounter(rc)
    val sb = new StringBuilder(replica).append(" [") // save stringbuilder?
    for (i <- 0 to (a.length - 1)) {
      if (b(i).toInt > a(i).toInt) {
        sb.append(b(i))
      } else {
        sb.append(a(i))
      }
      sb.append(',')
    }
    sb.deleteCharAt(sb.length() - 1).append("]\"").toString()
  }

  // Expected format is [x,y,...,z]".
  private def splitCounter(s: String): Array[String] = {
    val a = s.split(",")
    a(0) = a(0).substring(1)
    a(a.length - 1) = a(a.length - 1).substring(0, a(a.length - 1).length - 2)
    a
  }
}

class ODSCounter(var c: String) extends Base[ODSCounter, Int] {
  override def merge(rhs: ODSCounter): Unit = {
    //
  }

  override def value: Int = {
    var sum = 0
    c.foreach(sum += _.toInt)
    sum
  }
}
