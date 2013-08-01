package org.mackler.bitcoincli

import akka.actor.{Actor,ActorLogging,ActorRef,ActorSystem,Props,Terminated}

import scala.concurrent.Await
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

import com.frugalmechanic.optparse._

import scala.tools.jline.console.history.FileHistory
import scala.tools.jline.console.ConsoleReader

import java.util.Date

object Shell extends OptParse {
  import Server._

  val wallet = StrOpt()
  val consoleReader: Option[ConsoleReader] = None
  val prompt = "bitcoinj> "
  var terminatorOption: Option[ActorRef]     = None
  def terminator                             = terminatorOption.get

  def main (args: Array[String]) {
    parse(args)
    val actorSystem = ActorSystem("BitcoinjCli")
    val bitcoins = actorSystem.actorOf( Props(classOf[Server],wallet.getOrElse("test.wallet")),"MainActor" )

    val terminator = actorSystem.actorOf(Props(classOf[Terminator],bitcoins))

    val consoleReader = Some(new ConsoleReader)
    val historyFile = (new java.io.File(".history")).getAbsoluteFile
    val history = new FileHistory(historyFile)
    consoleReader.get.setHistory(history)
    var line = ""
    var exiting: Boolean = false
    implicit val timeout = Timeout(5.seconds)

    try {
      Await.result(bitcoins ? AreStarted, timeout.duration)
    } catch {
      case e: Exception => sys.exit(1)
    }

    println("Welcome to the interactive bitcoinj shell")

    do {
      line = consoleReader.get.readLine(prompt)
      if (line == null) exiting = true else {
      val args = line.split("[\\s]+")
      args(0) match {

        case "status" ⇒
          val future = bitcoins ? HowMuchDownloaded
          val result = Await.result(future, timeout.duration).asInstanceOf[Float]
	  if (result == 0) {
	    val (have,want) = Await.result(bitcoins ? HowManyPeers, timeout.duration).
	                      asInstanceOf[Tuple2[Int,Int]]
	    println(s"Connected to $have of $want peers")
          } else if (result < 100.0)
            println(s"Downloading block chain, $result percent completed")
          else {
            val s = actorSystem.uptime
            println(String.format("Up %d:%02d:%02d",
				  (s/3600).asInstanceOf[AnyRef],
				  ((s%3600)/60).asInstanceOf[AnyRef],
				  ((s%60)).asInstanceOf[AnyRef]
				))
	  }

	case "wallet" ⇒
	  val contents = Await.result(bitcoins ? WhatContents, timeout.duration).
	  asInstanceOf[WalletContents]
          println(s"The wallet contains ${contents.addresses.size} addresses")
          contents.addresses.foreach {
	    address ⇒ println(s"Key Address: $address")
          }
	  println(s"Balance in microcents: avail. ${contents.availableBalance}; est. ${contents.estimatedBalance}")
	  if (contents.unconfirmed.size > 0) {
	    println("Unconfirmed transactions pending:")
            contents.unconfirmed.foreach(println)
	  }

	case "peers" ⇒
          val peers = Await.result(bitcoins ? WhoArePeers, timeout.duration).asInstanceOf[List[String]]
	  if (peers.size > 0) peers.foreach(println)
	  else println("No peers connected")

	case "pay" ⇒
	  if (args.length != 3) println("usage: pay <address> <microcents>")
	  else try {
	    val amount = BigInt(args(2))
            Await.result(bitcoins ? Payment(args(1), amount), timeout.duration) match {
              case t: Throwable ⇒ println(s"Payment failed; ${t.getMessage}")
              case hash: String ⇒ println(s"$hash broadcast at ${new Date()}")
            }
	  } catch {
	    case e: NumberFormatException ⇒ println("Invalid payment amount")
	  }

	case "replay" ⇒ bitcoins ! Replay

	case "exit" ⇒ exiting = true
	case "quit" ⇒ exiting = true

	case "help" ⇒ println(
	  """|These commands are available in the shell:
             |  status                   Display information about the running system.
             |  wallet                   Display information about the wallet.
             |  peers                    List all currently connected peers.
             |  pay <address> <amount>   Send the indicated number of microcents.
             |  replay                   Clear transactions from wallet and download block chain.
             |  exit | quit              Exit this shell.
             |  help                     Display this help.""".stripMargin
	)

        case _ ⇒ if (args.size > 0 && line != "") println(s"unknown command: ${args(0)}")
      }}
    } while (!exiting)
    bitcoins ! Terminate
  }

  class Terminator(app: ActorRef) extends Actor with ActorLogging {
    context watch app
    def receive = {
      case Terminated(_) ⇒
	log.info("Bitcoins Actor has terminated, shutting down")
        context.system.shutdown()
        println("exiting")
        consoleReader.get.getHistory.asInstanceOf[FileHistory].flush()
        System.exit(0)
    }
  }

}


/*
class SupervisorStrategy extends akka.actor.SupervisorStrategyConfigurator {
  def create: akka.actor.AllForOneStrategy = {
    case _ : ⇒ 
  }
}
*/