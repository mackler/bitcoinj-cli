package org.mackler.bitcoincli

import com.google.bitcoin.core.Utils._

import akka.actor.{Actor,ActorLogging,ActorRef,ActorSystem,Props,Terminated}
import akka.pattern.ask
import akka.util.Timeout

import scala.tools.jline.console.ConsoleReader
import scala.tools.jline.console.completer.StringsCompleter
import scala.tools.jline.console.history.FileHistory

import com.frugalmechanic.optparse._

import scala.concurrent.Await
import scala.concurrent.duration._

import java.util.Date

object Shell extends OptParse {
  import Server._
  System.setProperty("jline.shutdownhook","true") // otherwise jline leaves terminal echo off
  implicit val j2bi = scala.math.BigInt.javaBigInteger2bigInt _

  val wallet = StrOpt()

  val consoleReader = new ConsoleReader
  val historyFile = (new java.io.File(".history")).getAbsoluteFile
  val history: FileHistory = new FileHistory(historyFile)
  consoleReader.setHistory(history)
  consoleReader.addCompleter( new StringsCompleter (
    "status","peers","wallet","pay","transaction","backup","quit","exit","help"
  ))

  val prompt = "bitcoinj> "
/*  var terminatorOption: Option[ActorRef]     = None
  def terminator                             = terminatorOption.get */

  def main (args: Array[String]) {
    parse(args)
    val actorSystem = ActorSystem("BitcoinjCli")
    val walletPrefix = wallet.getOrElse("default").replaceAll("\\.wallet$","")
    val bitcoins = actorSystem.actorOf(
      Props(classOf[Server], walletPrefix),
      "bitcoinService"
    )

    val terminator = actorSystem.actorOf(Props(classOf[Terminator],bitcoins))

    var line = ""
    var exiting: Boolean = false

    try {
      implicit val timeout = Timeout(30.seconds)
      val promise = bitcoins ? AreStarted
      Await.result(promise, timeout.duration)
    } catch {
      case e: java.util.concurrent.TimeoutException ⇒
        println("System startup too took long; giving up")
        actorSystem.shutdown()
	sys.exit(2)
      case e: Exception =>
	println(s"startup error $e")
	sys.exit(1)
    }

    print("Welcome to the interactive bitcoinj shell.\n")

    implicit val timeout = Timeout(5.seconds)

    do {
      line = consoleReader.readLine(prompt)
      if (line == null) exiting = true else {
      val args = line.split("[\\s]+")
      args(0) match {

        case "status" ⇒
          val downloadProgress = Await.result( (bitcoins ? HowMuchDownloaded), timeout.duration).
                                 asInstanceOf[Float]
          val peerCount = Await.result(bitcoins ? HowManyPeers, timeout.duration).
	                              asInstanceOf[Int]
          print(s"Connected to $peerCount peer${ peerCount match { case 1 => ""; case _ => "s"} }.  ")
	  if (downloadProgress == 0) {
            println("Block chain download not started yet.")
          } else if (downloadProgress < 100.0)
            printf("Block Chain is %.0f%% downloaded.\n", downloadProgress)
          else {
            val s = actorSystem.uptime
            println(String.format("Uptime: %d:%02d:%02d",
				  (s/3600).asInstanceOf[AnyRef],
				  ((s%3600)/60).asInstanceOf[AnyRef],
				  ((s%60)).asInstanceOf[AnyRef]
				))
	  }

	case "wallet" ⇒
	  val contents = Await.result(bitcoins ? WhatContents, timeout.duration).
	  asInstanceOf[WalletContents]
          println(s"The wallet file ${walletPrefix}.wallet contains ${contents.addresses.size} address${
	    if (contents.addresses.size != 1) "es" else ""
	  }:")
          contents.addresses.foreach {
	    address ⇒ println(s"  $address")
          }

	  val transactions = Await.result(bitcoins ? WhatTransactions, timeout.duration).
	                     asInstanceOf[List[TxData]]
          if(transactions.size > 0) println(formatTxs(transactions))

	  println("Wallet balance in BTC: " + {
	    val available = bitcoinValueToFriendlyString(contents.availableBalance)
	    val estimated = bitcoinValueToFriendlyString(contents.estimatedBalance)
	    if (available == estimated) available.toString
            else s"available: $available; estimated: $estimated"
	  })
	  if (contents.unconfirmed.size > 0) {
	    println("Unconfirmed transactions pending:")
            contents.unconfirmed.foreach(println)
	  }

	case "peers" ⇒
          val peers = Await.result(bitcoins ? WhoArePeers, timeout.duration).asInstanceOf[List[String]]
	  if (peers.size > 0) peers.foreach(println)
	  else println("No peers connected")

	case "pay" ⇒
	  if (args.length != 3) println("usage: pay <address> <bitcoins>")
	  else try {
	    val amount = toNanoCoins(args(2))
            Await.result(bitcoins ? Payment(args(1), amount), timeout.duration) match {
              case t: Throwable ⇒ println(s"Payment failed; ${t.getMessage}")
              case hash: String ⇒ println(s"$hash broadcast at ${new Date()}")
            }
	  } catch {
	    case e: NumberFormatException ⇒ println("Invalid payment amount")
	    case e: ArithmeticException ⇒ println("Amount fraction or range error")
	  }

	case "transaction" => args.length match {
	  case 2 => Await.result(bitcoins ? TxInquiry(args(1)), timeout.duration) match {
              case Left(reason: String)    ⇒ println(reason)
              case Right(txString: String) ⇒ println(txString)
            }
          case _ => println("usage: transaction <id>")
	}

	case "backup" => args.length match {
	  case 2 => Await.result(bitcoins ? MakeBackup(args(1)), timeout.duration) match {
              case Left(reason: String) => println(s"FAIL: $reason")
              case Right((name: String,size: Long)) =>
		println(s"Wrote ${size.toString} bytes to file $name")
            }

          case _ => println("usage: backup <filename>")
	}

	case "replay" ⇒ bitcoins ! Replay

	case "exit" ⇒ exiting = true
	case "quit" ⇒ exiting = true

	case "help" ⇒ println(
	  """|These commands are available in this shell:
             |  status                   Display information about the running system.
             |  wallet                   Display information about the wallet.
             |  peers                    List all currently connected peers.
             |  pay <address> <amount>   Send the indicated number of Bitcoins.
             |  transaction <id>         Display details of the given transaction.
             |  backup <filename>        Make a backup copy of the wallet.
             |  exit | quit              Exit this shell.
             |  help                     Display this help.""".stripMargin
	)

        case _ ⇒ if (args.size > 0 && line != "") println(s"unknown command: ${args(0)}")
      }}
    } while (!exiting)
    val fileHistory = consoleReader.getHistory.asInstanceOf[FileHistory]
    consoleReader.getHistory.asInstanceOf[FileHistory].flush()
    bitcoins ! Terminate
  }

  class Terminator(app: ActorRef) extends Actor with ActorLogging {
    context watch app
    def receive = {
      case Terminated(_) ⇒
	log.info("Bitcoins Actor has terminated, shutting down")
        context.system.shutdown()
        print("exiting...")
        System.exit(0)
    }
  }

  private def formatTxs(transactions: List[TxData]): String = {
    val fmt = java.text.DateFormat.
              getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
    val bals: List[Tuple4[Date, BigInt, BigInt, String]] =
	transactions.reverse.scanLeft((new Date, BigInt(0), BigInt(0),"")) { case (r,c) =>
	  val bal = r._3 + c.amount
	  (c.date, c.amount, bal, c.hash)
      }.tail
      val strings: List[Tuple5[String,String,String,String,String]] =
	("Date","Dr.","Cr.","Bal.","Transaction ID") ::
	bals.map(t => (
	  fmt.format(t._1),
	  (if(t._2 > 0) bitcoinValueToFriendlyString(t._2) else ""),
	  (if(t._2 < 0) bitcoinValueToFriendlyString(t._2) else ""),
	  bitcoinValueToFriendlyString(t._3),
	  t._4
        ))
      val maxDate = strings.map(t => t._1.length).max
      val maxDr = strings.map(t => t._2.length).max
      val maxCr = strings.map(t => t._3.length).max
      val maxBal = strings.map(t => t._4.length).max
      strings.map { t =>
	t._1.formatted(s"%${maxDate}s") + "  " +
	t._2.formatted(s"%${maxDr}s") + "  " +
	t._3.formatted(s"%${maxCr}s") + "  " +
	t._4.formatted(s"%${maxBal}s") + "  " +
	t._5
      }.mkString("\n")
  }

}
