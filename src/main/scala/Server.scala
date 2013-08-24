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

import akka.actor.{Actor,ActorLogging,Props}

import grizzled.slf4j.Logging

import collection.JavaConversions._

import java.io.{File, IOException}
import java.math.BigInteger
import java.util.Date

class Server(walletName: String) extends Actor with ActorLogging with Logging {
  import Server._

  private val walletFile = new File(walletName)

  var wallet:    Wallet     = null
  var chain:     BlockChain = null
  var peerGroup: PeerGroup  = null

  var downloadPercentage: Float = 0

  override def preStart() {
    wallet = try {
      initializeWallet(walletFile, networkParams)
    } catch {
      case e: UnreadableWalletException ⇒
        println(s"Failure reading wallet: ${e.getMessage}")
        context.stop(self)
        null
    }

    bringUpNetwork()
  }

  private def bringUpNetwork() {
    chain     = initializeBlockChain(networkParams, wallet)
    peerGroup = initializePeerGroup(networkParams, chain, wallet)

    val downloadListener = new DownloadListener {
      override def startDownload(block: Int) {}
      override def progress(pct: Double, blocksSoFar: Int, date: Date) {
	self ! DownloadProgress(pct.toFloat)
      }
      override def doneDownload() {
	wallet.saveToFile(walletFile)
	downloadPercentage = 100
      }
    }

    peerGroup.startBlockChainDownload(downloadListener)
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

    case WhoArePeers ⇒
      sender ! peerGroup.getConnectedPeers.map(_.getAddress.toString).toList

    case HowManyPeers ⇒
      sender ! (peerGroup.numConnectedPeers, peerGroup.getMinBroadcastConnections)

    case Payment(address, amount) ⇒
      if (amount < MIN_NONDUST_OUTPUT)
        sender ! new Exception(s"Amount must be at least $MIN_NONDUST_OUTPUT")
      else {
        val request = SendRequest.to( new Address(networkParams, address), amount.bigInteger )
        // TODO: Next line throws KeyCrypterException if ECKey lacks private key necessary for signing.
        if (wallet.completeTx(request)) {
          wallet.commitTx(request.tx)
          wallet.saveToFile(walletFile)
          val broadcastTransaction = peerGroup.broadcastTransaction(request.tx)
	  Futures.addCallback(broadcastTransaction, new FutureCallback[Transaction] {
	    def onSuccess(result: Transaction) { println(s"Payment propagated at ${new Date()}\n$result") }
	    def onFailure(t: Throwable) { println(s"Payment failed ${t.getMessage}") }
	  })
	  sender ! request.tx.getHashAsString
        } else {
          sender ! new Exception("insufficient balance")
        }
      }

    case Replay ⇒
      downloadPercentage = 0
      chain.getBlockStore.close()
      chainFile.delete()
      wallet.clearTransactions(0)
      peerGroup.stopAndWait()
      bringUpNetwork()
  }

  override def postStop() {
    log.info("Main actor stopping")
    chain.getBlockStore.close()
    wallet.saveToFile(walletFile)
    peerGroup.stop()
  }

}

object Server extends Logging {

  private val networkParams = TestNet3Params.get()
  private final val chainFile = new File("spvchain")

  private def initializeWallet(file: File, networkParams: NetworkParameters): Wallet = {
    val wallet = if (!file.exists()) {
	println(s"Creating new wallet file $file")
	val w = new Wallet(networkParams)
        w.addKey(new ECKey)
        w.saveToFile(file)
        w
    } else {
      println(s"Using existing wallet file $file")
      Wallet.loadFromFile(file)
    }

    wallet.addEventListener(new AbstractWalletEventListener {
      override def onCoinsSent(w: Wallet,
			       tx: Transaction,
			       prevBalance: BigInteger,
			       newBalance: BigInteger ) {
	super.onCoinsSent(w, tx, prevBalance, newBalance)
	info(s"onCoinsSent listener called: $prevBalance -> $newBalance\n$tx")
      }
      override def onCoinsReceived(
	w: Wallet,
	tx: Transaction,
	prevBalance: BigInteger,
	newBalance: BigInteger
      ) { synchronized {
	wallet.saveToFile(file)
        println(s"ALERT: you just received ${tx.getValueSentToMe(wallet)} microcents")
        println(s"  transaction ${tx.toString(null)}")
        println(s"  Previous balance: BTC ${bitcoinValueToFriendlyString(prevBalance)}")
        println(s"  New balance: BTC ${bitcoinValueToFriendlyString(newBalance)}")
      }}
    })

    wallet
  }

  private def initializeBlockChain(networkParams: NetworkParameters, wallet: Wallet): BlockChain = {
    if (!chainFile.exists) wallet.clearTransactions(0)
    val blockStore = new SPVBlockStore(networkParams, chainFile)
    new BlockChain(networkParams, wallet, blockStore)
  }

  private def initializePeerGroup(
    networkParams: NetworkParameters,
    chain: BlockChain,
    wallet: Wallet
  ): PeerGroup = {
    val peerGroup = new PeerGroup(networkParams, chain)
    peerGroup.setUserAgent(BuildInfo.name, BuildInfo.version)
    peerGroup.addPeerDiscovery(
      // new DnsDiscovery(networkParams)
      // if the above line doesn't work try the next line instead
      Discovery
    )
    peerGroup.addWallet(wallet)
    peerGroup.startAndWait()
    info(s"Peer group $peerGroup service has finished starting")
    peerGroup
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
