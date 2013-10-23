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

object Shell extends OptParse with OptParseImplicits {
  import Server._
  System.setProperty("jline.shutdownhook","true") // otherwise jline leaves terminal echo off

  val wallet = StrOpt(desc = "Specify a wallet filename")
  val notest = BoolOpt(desc = "Connect to the main Bitcoin network, not the test net")
  val dns = BoolOpt(desc = "Discover peers using DNS")

  val consoleReader = new ConsoleReader
  val historyFile = (new java.io.File(".history")).getAbsoluteFile
  val history: FileHistory = new FileHistory(historyFile)
  consoleReader.setHistory(history)
  consoleReader.addCompleter( new StringsCompleter (
    "status","peers","wallet","pay","transaction","backup","encrypt","decrypt","quit","exit","help"
  ))

  val prompt = "bitcoinj> "

  import DiscoveryType._
  import NetworkId._

  def main (args: Array[String]) {
    parse(args)

    if(notest) println ("Connecting to the main Bitcoin network is not implemented yet.")
    val networkId = TEST

    def defaultDiscovery(net: NetworkId.Value): DiscoveryType.Value = net match {
      case MAIN => DNS
      case TEST => HARD
    }

    val actorSystem = ActorSystem("BitcoinjCli")

    val discoveryType = dns.value match {
      case Some(true) => DNS
      case _    => defaultDiscovery(networkId)
    }

    val bitcoins = actorSystem.actorOf(
      Props(classOf[Server], wallet.value, networkId, discoveryType),
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
          print(s"Connected to $peerCount peer${ peerCount match { case 1 => ""; case _ => "s"} } on the Bitcoin ${
	    networkId.toString.toLowerCase
	  } network.  ")
	  if (downloadProgress == 0) {
            println("Block chain download not started yet.")
          } else if (downloadProgress < 100.0)
            printf("Block Chain %.0f%% downloaded.\n", downloadProgress)
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
          println(s"Wallet ${contents.filename}: encryption is ${
	    contents.isEncrypted match {
	      case true => "ON"
              case false => "OFF"
	    }
	  }; contains ${contents.addresses.size} address${
	    if (contents.addresses.size != 1) "es" else ""
	  }:")
          contents.addresses.foreach {
	    address ⇒ println("  " + address)
          }

	  val transactions = contents.transactions
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

	case "pay" ⇒ args.length match {
	  case 3 => try {
	    val microCents: java.math.BigInteger = toNanoCoins(args(2))
	    def tryPayment(address: String, amount: java.math.BigInteger, password: Option[String]) {
              Await.result(bitcoins ? Payment(args(1), amount, password), timeout.duration) match {
		case Left(t: Exception) ⇒ println(s"Payment failed: ${t.getMessage}")
		case Right(hash: String) ⇒ println(s"Transaction $hash sent at ${new Date()}")
		case PasswordNeeded => tryPayment(address, amount, readPassword())
	      }
            }
	    tryPayment(args(1), microCents, None)
	  } catch {
	    case e: NumberFormatException ⇒ println("Amount must be a number")
	    case e: ArithmeticException ⇒ println("Amount too big or small")
	  }

	  case _ => println("usage: pay <address> <bitcoins>")
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

	case "encrypt" =>
	  val proceed = args.length match {
	    case 2 => Some(args(1))
	    case 1 => readPassword(requireConfirmation = true, warning = Some("Losing your password means losing your wallet."))
	  }
	  proceed match {
	    case None => println("Wallet encryption canceled")
	    case Some(password) => Await.result(bitcoins ? Encrypt(password), timeout.duration) match {
	      case Left(reason: String) => println(s"Wallet encryption failed: $reason")
	      case Right(_)             => println("Wallet is now encrypted")
	    }
	  }

	case "decrypt" => Await.result(bitcoins ? Decrypt(args.length match {
	  case 1 => readPassword().get
	  case 2 => args(1)
        }), timeout.duration) match {
	  case Left(reason: String) => println(s"Wallet decryption failed: $reason")
	  case Right(_)             => println("Wallet is now unencrypted")
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
             |  encrypt [ password ]     Encrypt the wallet.
             |  decrypt [ password ]     Decrypt the encrypted wallet.
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

  private def readPassword(requireConfirmation: Boolean = false, warning: Option[String] = None): Option[String] = {
    val reader = new ConsoleReader
    def warnedRead(echo: Boolean = false): Option[String] = {
      def read(prompt: String) = echo match {
	case false => reader.readLine(prompt, new Character('*'))
	case true => reader.readLine(prompt)
      }
      val first = read("Enter password: ")
      (requireConfirmation && echo == false) match {
	case false => Some(first)
	case true =>
	  val second = read("Confirm password: ")
	  (first == second) match {
	    case false => None
	    case true => Some(first)
	  }
      }
    }
    warning match {
      case None => warnedRead()
      case Some(warning) =>
	println(s"WARNING: $warning")
        reader.readLine("Proceed? [y]es, [no], [e]cho password while typing: ").
               substring(0,1).toLowerCase match {
	  case "y" => warnedRead()
          case "e" => warnedRead(echo = true)
	  case _ => None // cancel
	}
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

object DiscoveryType extends Enumeration {
  val DNS = Value
  val HARD = Value
}

object NetworkId extends Enumeration {
  val MAIN = Value
  val TEST = Value
}

