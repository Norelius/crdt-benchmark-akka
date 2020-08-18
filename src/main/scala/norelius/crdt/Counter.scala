package norelius.crdt

trait Counter {
  /**
   * ID of the counter.
   *
   * @return
   */
  def replica(): Int

  /**
   * Increments the counter by 1.
   */
  def increment()

  /**
   * The sum of the counter.
   *
   * @return
   */
  def value(): Int

  /**
   * Merges this counter with another serialized counter.
   *
   * @param other serialized representation of a GrowOnlyCounter.
   */
  def merge(other: String)

  // Add batch merging here if needd.

  /**
   * Returns a serialized representation of this counter.
   *
   * @return
   */
  def serialize(): String
}


// Counter is stored deserialized. OSD merge is not used.
class deserNonOsdCounter(counter: GrowOnlyCounter,
                         serialize: GrowOnlyCounter => String,
                         deserialize: String => GrowOnlyCounter)
  extends Counter {
  override def replica(): Int = counter.replica

  override def increment(): Unit = counter.update(1)

  override def value(): Int = counter.value

  override def merge(other: String): Unit = counter.merge(deserialize(other))

  override def serialize(): String = serialize(counter)
}


// Counter is stored serialized. OSD merge is not used.
class serNonOsdCounter(var counter: String,
                       serialize: GrowOnlyCounter => String,
                       deserialize: String => GrowOnlyCounter)
  extends Counter {

  def this(counter: GrowOnlyCounter,
           serialize: GrowOnlyCounter => String,
           deserialize: String => GrowOnlyCounter) {
    this(serialize(counter), serialize, deserialize)
  }

  override def replica(): Int = deserialize(counter).replica

  override def increment(): Unit = {
    val tmp = deserialize(counter)
    tmp.update(1)
    counter = serialize(tmp)
  }

  override def value(): Int = deserialize(counter).value

  override def merge(other: String): Unit = {
    val tmp = deserialize(counter)
    tmp.merge(deserialize(other))
    counter = serialize(tmp)
  }

  override def serialize(): String = counter
}


// Counter is stored serialized. OSD merge is used.
class serOsdCounter(var counter: String,
                    serialize: GrowOnlyCounter => String,
                    deserialize: String => GrowOnlyCounter)
  extends Counter {

  def this(counter: GrowOnlyCounter,
           serialize: GrowOnlyCounter => String,
           deserialize: String => GrowOnlyCounter) {
    this(serialize(counter), serialize, deserialize)
  }

  override def replica(): Int = deserialize(counter).replica

  override def increment(): Unit = {
    val tmp = deserialize(counter)
    tmp.update(1)
    counter = serialize(tmp)
  }

  override def value(): Int = deserialize(counter).value

  override def merge(other: String): Unit = {
    counter = GrowOnlyCounter.mergePickle(counter, other)
  }

  override def serialize(): String = counter
}
