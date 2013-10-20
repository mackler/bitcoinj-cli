package org.mackler.bitcoincli

import com.google.bitcoin.core.{AbstractWalletEventListener,Address,BlockChain,DownloadListener,
				ECKey,NetworkParameters,PeerGroup,Transaction,Wallet}
import com.google.bitcoin.core.Utils._
import com.google.bitcoin.core.Wallet.SendRequest
import com.google.bitcoin.core.Transaction.MIN_NONDUST_OUTPUT
import com.google.bitcoin.discovery.DnsDiscovery
import com.google.bitcoin.params.TestNet3Params
import com.google.bitcoin.store.SPVBlockStore
import com.google.bitcoin.store.UnreadableWalletException

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.FutureCallback
import com.google.bitcoin.kits.WalletAppKit

import akka.actor.{Actor,ActorLogging,Props}

//import grizzled.slf4j.Logging

import collection.JavaConversions._

import java.io.{File, IOException}
import java.math.BigInteger
import java.util.Date

class Server(walletPrefix: String) extends Actor with ActorLogging {
  import Server._

  var downloadPercentage: Float = 0

  val walletAppKit = (new WalletAppKit(networkParams, new java.io.File("."), walletPrefix) {
    override def onSetupCompleted() {
      log.debug(s"Bitcoin wallet has ${wallet.getKeychainSize} keys in its keychain")
      log.debug("Starting download of block chain")
      wallet addEventListener walletEventListener
    }
  }).
    setPeerNodes (Discovery.peerAddresses: _*).
    setDownloadListener {
      new DownloadListener {
	override def startDownload(block: Int) {}
	override def progress(pct: Double, blocksSoFar: Int, date: Date) {
	  self ! DownloadProgress(pct.toFloat)
	}
	override def doneDownload() {
	  self ! DownloadProgress(100.toFloat)
	  println("Block chain download is complete.")
	}
      }
    }.
    setAutoSave (true).
    setBlockingStartup (false)

  walletAppKit.start()

  def wallet = walletAppKit.wallet
  def chain =  walletAppKit.chain
  def peerGroup =  {
    val pg = walletAppKit.peerGroup
    if (pg != null) Some(pg) else None
  }

  def receive = {

    case AreStarted ⇒ sender ! true

    case Terminate ⇒ context.stop(self)

    case DownloadProgress(pct) ⇒ downloadPercentage = pct

    case HowMuchDownloaded ⇒ sender ! downloadPercentage

    case WhatContents ⇒ sender ! WalletContents(
      wallet.getKeys.map(_.toAddress(networkParams).toString).toList,
      wallet.getBalance,
      wallet.getBalance(com.google.bitcoin.core.Wallet.BalanceType.ESTIMATED),
      wallet.getPendingTransactions.map(_.getHashAsString).toList
    )

    case WhatTransactions ⇒ sender ! 
      wallet.getTransactionsByTime.map(t =>
	TxData(t.getUpdateTime, t.getConfidence.getDepthInBlocks, t.getHashAsString, t.getValue(wallet))
    ).toList

    case WhoArePeers ⇒ sender ! (peerGroup match {
	case Some(pg) => pg.getConnectedPeers.map(_.getAddress.toString).toList
	case None     => List[String]()
      })

    case HowManyPeers ⇒ sender ! (peerGroup match {
      case None => 0
      case Some(pg) => pg.numConnectedPeers
    })

    case Payment(address, amount) ⇒
      val pg = peerGroup
      if( ! pg.isDefined ) sender ! new Exception("Peer Group is not yet initialized.  Please try again later.")
      else {
	if (amount < MIN_NONDUST_OUTPUT)
          sender ! new Exception(s"Amount must be at least $MIN_NONDUST_OUTPUT")
	else {
          val request = SendRequest.to( new Address(networkParams, address), amount.bigInteger )
          // TODO: Next line throws KeyCrypterException if ECKey lacks private key necessary for signing.
          if (wallet.completeTx(request)) {
            wallet.commitTx(request.tx)
            val broadcastTransaction = pg.get.broadcastTransaction(request.tx)
	    Futures.addCallback(broadcastTransaction, new FutureCallback[Transaction] {
	      def onSuccess(result: Transaction) { println(s"Payment propagated at ${new Date()}\n$result") }
	      def onFailure(t: Throwable) { println(s"Payment failed ${t.getMessage}") }
	    })
	    sender ! request.tx.getHashAsString
          } else {
            sender ! new Exception("insufficient balance")
          }
	}
      }

/*    case Replay ⇒
      downloadPercentage = 0
      chain.getBlockStore.close()
      chainFile.delete()
      wallet.clearTransactions(0)
      peerGroup.stopAndWait()
      bringUpNetwork()*/

      case m => log.warning(s"Ignoring unrecognized ${m.getClass.getSimpleName} message: $m")

  }

}

object Server {

  val log = org.slf4j.LoggerFactory.getLogger(this.getClass)

  private val networkParams = TestNet3Params.get()
  private final val chainFile = new File("spvchain")

  val walletEventListener = new AbstractWalletEventListener {
    override def onCoinsSent(w: Wallet,
			     tx: Transaction,
			     prevBalance: BigInteger,
			     newBalance: BigInteger ) {
      super.onCoinsSent(w, tx, prevBalance, newBalance)
      log.info(s"onCoinsSent listener called: $prevBalance -> $newBalance\n$tx")
    }
    override def onCoinsReceived(
      wallet: Wallet,
      tx: Transaction,
      prevBalance: BigInteger,
      newBalance: BigInteger
    ) { synchronized {
      println(s"ALERT: you just received ${tx.getValueSentToMe(wallet)} microcents")
      println(s"  transaction ${tx.toString(null)}")
      println(s"  Previous balance: BTC ${bitcoinValueToFriendlyString(prevBalance)}")
      println(s"  New balance: BTC ${bitcoinValueToFriendlyString(newBalance)}")
    }}
  }

  case object Terminate
  case class DownloadProgress(percentage: Float)
  case object HowMuchDownloaded
  case object WhatContents
  case class WalletContents(addresses: List[String],
			    availableBalance: BigInt,
			    estimatedBalance: BigInt,
			    unconfirmed: List[String])
  case object WhatTransactions
  case class TxData(date: Date, depth: Int, hash: String, amount: BigInt)
  case class Payment(address: String, amount: BigInt)
  type Error = Option[Exception]
  case object WhoArePeers
  case object HowManyPeers
  case object Replay
  case object AreStarted
}
