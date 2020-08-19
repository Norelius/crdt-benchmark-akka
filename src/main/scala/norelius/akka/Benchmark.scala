package norelius.akka

import akka.actor.typed.ActorSystem
import norelius.akka.ReplicaManager.{Setup, Start}
import norelius.akka.Serializer.Serializer
import norelius.akka.SimpleCounter.{SendBehavior, _}

import scala.concurrent.Await
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object Benchmark extends App {
  // The number of replicas in the network.
  var numberOfReplicas = 5
  // The number of clients sending updates per replica.
  var clientsPerReplica = 1
  // The average percentage of replicas in the network that a replica should
  // send it's state to when it send the state.
  var sendBehavior: SendBehavior = Ratio(0.5)
  // How often a replica should share it's state with other replicas.
  var sendFrequency = 500.milliseconds
  // If replicas should shut down after a certain number of queries.
  val finiteQueries = true
  // Total number of queries sent
  var totalQueries: Int = 2000000
  // The number of queries until a replica shuts down.
  var maxQueries: Int = 0
  // The number of update messages per client per replica that should be sent.
  var numberOfQueriesPerClient = 0
  setQueries(totalQueries)
  // The number of times the experiment should be run.
  var runs = 1

  var storedSerialized = true
  var osdMerge = false
  var serializer = Serializer.Pickle

  parseArgs()

  for (i <- 1 to runs) {

    val replicaManager: ActorSystem[ReplicaManager.Command] = ActorSystem(ReplicaManager(), "ReplicaManager")
    // Log the run info so logs are easier to tell apart.
    replicaManager.log.info("Experiment: {}, Runs: {}, Replicas: {}, Clients: {}, Sendbehavior: {}, Sendfrequency: {}," +
      " totalQueries: {}, storedSerialized: {}, osdMerge: {}, serializer: {}", i, runs, numberOfReplicas,
      clientsPerReplica * numberOfReplicas, sendBehavior, sendFrequency, totalQueries, storedSerialized,
      osdMerge, serializer)
    replicaManager ! Setup(numberOfReplicas,
      storedSerialized,
      osdMerge,
      serializer,
      Config(sendBehavior, sendFrequency, finiteQueries, maxQueries),
      clientsPerReplica)
    Thread.sleep(50) // Make sure all replicas and client are up and running.
    replicaManager ! Start(numberOfQueriesPerClient)
    val future = replicaManager.whenTerminated
    Await.result(future, 30.seconds)
    print {
      "."
    }
  }

  private def parseArgs(): Unit = {
    val usage =
      """
    Usage: benchmark [-replicas num] [-messages num] [-runs num] [-clients-per-replica num]
                     [-gossip one/all/double] [-gossip-frequency ms]
                     [-stored-deserialized] [-osd-merge] [-serializer pickle/java]
  """
    if (args.length == 0) println(usage)
    val arglist = args.toList
    type OptionMap = Map[Symbol, Any]

    def nextOption(map: OptionMap, list: List[String]): OptionMap = {

      list match {
        case Nil => map
        case "-replicas" :: str :: tail =>
          nextOption(map ++ Map(Symbol("replicas") -> str.toInt), tail)
        case "-messages" :: str :: tail =>
          nextOption(map ++ Map(Symbol("maxQueries") -> str.toInt), tail)
        case "-runs" :: str :: tail =>
          nextOption(map ++ Map(Symbol("runs") -> str.toInt), tail)
        case "-clients-per-replica" :: str :: tail =>
          nextOption(map ++ Map(Symbol("clientsPerReplica") -> str.toInt), tail)
        case "-gossip" :: str :: tail =>
          val b: SendBehavior = str match {
            case "one" => One()
            case "all" => All()
            case r => Ratio(r.toDouble)
          }
          nextOption(map ++ Map(Symbol("sendBehavior") -> b), tail)
        case "-gossip-frequency" :: str :: tail =>
          nextOption(map ++ Map(Symbol("sendFrequency") -> str.toInt.milliseconds), tail)
        case "-stored-deserialized" :: tail =>
          nextOption(map ++ Map(Symbol("storedDeserialized") -> true), tail)
        case "-osd-merge" :: tail =>
          nextOption(map ++ Map(Symbol("osd-merge") -> true), tail)
        case "-serializer" :: str :: tail =>
          val s: Serializer = str match {
            case "pickle" => Serializer.Pickle
            case "java" => Serializer.Java
          }
          nextOption(map ++ Map(Symbol("serializer") -> s), tail)
        case option :: tail => println("Unknown option " + option)
          scala.sys.exit(1)
      }
    }

    val options = nextOption(Map(), arglist)

    if (options.contains(Symbol("replicas")))
      numberOfReplicas = options(Symbol("replicas")).asInstanceOf[Int]
    if (options.contains(Symbol("clientsPerReplica")))
      clientsPerReplica = options(Symbol("clientsPerReplica")).asInstanceOf[Int]
    if (options.contains(Symbol("sendBehavior")))
      sendBehavior = options(Symbol("sendBehavior")).asInstanceOf[SendBehavior]
    if (options.contains(Symbol("sendFrequency")))
      sendFrequency = options(Symbol("sendFrequency")).asInstanceOf[FiniteDuration]
    if (options.contains(Symbol("maxQueries"))) {
      setQueries(options(Symbol("maxQueries")).asInstanceOf[Int])
    }
    if (options.contains(Symbol("runs")))
      runs = options(Symbol("runs")).asInstanceOf[Int]
    if (options.contains(Symbol("storedDeserialized")))
      storedSerialized = false
    if (options.contains(Symbol("osd-merge")))
      osdMerge = true
    if (options.contains(Symbol("serializer")))
      serializer = options(Symbol("serializer")).asInstanceOf[Serializer]
  }

  private def setQueries(n: Int): Unit = {
    totalQueries = (n / numberOfReplicas / clientsPerReplica) * numberOfReplicas * clientsPerReplica
    maxQueries = totalQueries / numberOfReplicas
    numberOfQueriesPerClient = maxQueries / clientsPerReplica
  }
}
