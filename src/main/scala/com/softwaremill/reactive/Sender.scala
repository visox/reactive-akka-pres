package com.softwaremill.reactive

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.stream.FlowMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.duration._

object Sender extends App with Logging {
  implicit val system = ActorSystem()
  val serverConnection = StreamTcp().outgoingConnection(new InetSocketAddress("localhost", 9181))

  val getLines = () => scala.io.Source.fromFile("/Users/adamw/projects/reactive-akka-pres/data/2008.csv").getLines()

  /*
  val flow = linesSource.via(serverConnection.flow).to(logCompleteSink)
   */

  val linesSource = Source(getLines).map { line => ByteString(line + "\n") }
  val logCompleteSink = Sink.onComplete(r => logger.info("Completed with: " + r))

  val graph = FlowGraph { implicit b =>
    import akka.stream.scaladsl.FlowGraphImplicits._

    val broadcast = Broadcast[ByteString]

    val logWindowFlow = Flow[ByteString]
      .groupedWithin(10000, 1.seconds)
      .map(group => group.map(_.size).foldLeft(0)(_ + _))
      .map(groupSize => logger.info(s"Sent $groupSize bytes"))

    linesSource ~> broadcast ~> serverConnection.flow ~> logCompleteSink
                   broadcast ~> logWindowFlow         ~> Sink.ignore
  }

  implicit val mat = FlowMaterializer()
  graph.run()
}
