package org.mackler.bitcoincli

import com.google.bitcoin.crypto.{KeyCrypter,KeyCrypterScrypt}
import com.google.bitcoin.core.{AbstractWalletEventListener,Address,BlockChain,DownloadListener,
				ECKey,NetworkParameters,PeerGroup,Sha256Hash,Transaction,Wallet}
import com.google.bitcoin.core.Utils._
import com.google.bitcoin.core.Wallet.SendRequest
import com.google.bitcoin.core.Transaction.MIN_NONDUST_OUTPUT
import com.google.bitcoin.discovery.DnsDiscovery
import com.google.bitcoin.store.{SPVBlockStore,UnreadableWalletException}

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.FutureCallback
import com.google.bitcoin.kits.WalletAppKit

import akka.actor.{Actor,ActorLogging,Props}

import collection.JavaConversions._

import java.io.{File, IOException}
import java.math.BigInteger
import java.util.Date

import DiscoveryType._
import NetworkId._

class Server(
  filenameOption: Option[String],
  networkId: NetworkId.Value = TEST,
  discoveryType: DiscoveryType.Value = DNS
) extends Actor with ActorLogging {
  import Server._

  var downloadPercentage: Float = 0
  lazy val networkParams = getNetworkParams(networkId)

  val walletPrefix = filenameOption match {
    case Some(string) => string.replaceAll("\\.wallet$","")
    case None => networkId.toString.toLowerCase
  }

  val walletAppKit = (new WalletAppKit(networkParams, new File("."), walletPrefix) {
    override def onSetupCompleted() {
      log.debug(s"Bitcoin wallet has ${wallet.getKeychainSize} keys in its keychain")
      log.debug("Starting download of block chain")
      wallet addEventListener walletEventListener
    }
  })

  (discoveryType match {
    case HARD => walletAppKit.setPeerNodes (Discovery.peerAddresses: _*)
    case DNS => walletAppKit
  }).
    setUserAgent(BuildInfo.name, BuildInfo.version).
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
    setBlockingStartup (false).
    start()

  def wallet = walletAppKit.wallet
  def chain =  walletAppKit.chain
  def peerGroup =  Option[PeerGroup](walletAppKit.peerGroup)

  def receive = {

    case AreStarted ⇒ sender ! true

    case Terminate ⇒ context.stop(self)

    case DownloadProgress(pct) ⇒ downloadPercentage = pct

    case HowMuchDownloaded ⇒ sender ! downloadPercentage

    case WhatContents ⇒ sender ! WalletContents(
      filename = walletPrefix + ".wallet",
      isEncrypted = wallet.isEncrypted(),
      addresses = wallet.getKeys.map { k =>
	k.toAddress(networkParams).toString
      }.toList,
      availableBalance = wallet.getBalance,
      estimatedBalance = wallet.getBalance(com.google.bitcoin.core.Wallet.BalanceType.ESTIMATED),
      transactions = wallet.getTransactionsByTime.map(t =>
	TxData(t.getUpdateTime, t.getConfidence.getDepthInBlocks, t.getHashAsString, t.getValue(wallet))
      ).toList,
      unconfirmed = wallet.getPendingTransactions.map(_.getHashAsString).toList
    )

    case TxInquiry(id) =>
      sender ! ( (Some(wallet.getTransaction(new Sha256Hash(id))): Option[Transaction]) match {
	case None => Left("No such transaction in this wallet")
	case Some(tx) => Right(tx.toString)
      })

    case MakeBackup(filename) => sender ! (try {
      val file = new File( filename + (filename.matches(".*wallet") match {
	case false => ".wallet"
	case true => ""
      }) )
      wallet.saveToFile(file)
      Right((file.getName,file.length))
    } catch {
      case e: Exception => Left(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
    })

    case Encrypt(password) => sender ! (try {
      val keyCrypter = new KeyCrypterScrypt
      val key = keyCrypter.deriveKey(password)
      wallet.encrypt(keyCrypter, key)
      Right()
    } catch {
      case e: Exception =>
	e.printStackTrace()
	Left(Option[String](e.getMessage).getOrElse(e.getClass.getSimpleName))
    })

    case Decrypt(password) => sender ! (try {
      wallet.decrypt(wallet.getKeyCrypter.deriveKey(password))
      Right()
    } catch {
      case e: Exception => Left(e.getMessage)
    })

    case WhoArePeers ⇒ sender ! (peerGroup match {
	case Some(pg) => pg.getConnectedPeers.map(_.getAddress.toString).toList
	case None     => List[String]()
      })

    case HowManyPeers ⇒ sender ! (peerGroup match {
      case None => 0
      case Some(pg) => pg.numConnectedPeers
    })

    case Payment(address, amount, passwordOption) => peerGroup match {
      case None => sender ! Left(new Exception("Peer Group is not yet initialized.  Please try again later."))
      case Some(peers) => (amount < MIN_NONDUST_OUTPUT) match {
        case true =>  sender ! Left(new Exception(s"Amount must be at least $MIN_NONDUST_OUTPUT microcents"))
        case false => try {
	  wallet.isEncrypted match {
            case false => sender ! pay(wallet, peers, address, amount, None)
            case true => passwordOption match {
	      case None =>
		log.info(s"missing password")

 sender ! PasswordNeeded
	      case Some(password) =>
		log.info(s"trying payment with password $password")
	        sender ! pay(wallet, peers, address, amount, Some(password))
            }
	  }
        } catch {
	  case e: Exception => sender ! Left(e)
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
  import NetworkId._
  val log = org.slf4j.LoggerFactory.getLogger(this.getClass)

  private def pay (wallet: Wallet,
		   peerGroup: PeerGroup,
		   address: String,
		   microcents: BigInt,
		   passwordOption: Option[String]
  ): Outcome = {
    val request = SendRequest.to( new Address(wallet.getNetworkParameters, address), microcents.bigInteger )
    passwordOption match {
      case Some(password) => request.aesKey = wallet.getKeyCrypter.deriveKey(password)
      case None =>
    }
    wallet.completeTx(request) match {
      case false => Left(new Exception("insufficient balance"))
      case true =>
        wallet.commitTx(request.tx)
        val broadcastTransaction = peerGroup.broadcastTransaction(request.tx)
        Futures.addCallback(broadcastTransaction, new FutureCallback[Transaction] {
	  def onSuccess(result: Transaction) { println(s"Payment propagated at ${new Date()}\n$result") }
	  def onFailure(t: Throwable) { println(s"Payment failed ${t.getMessage}") }
	})
        Right(request.tx.getHashAsString)
    }
  }

  private def getNetworkParams(networkId: NetworkId.Value) = networkId match {
    case TEST => com.google.bitcoin.params.TestNet3Params.get()
    case MAIN => com.google.bitcoin.params.MainNetParams.get()
  }
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

  case class Encrypt(password: CharSequence)
  case class Decrypt(password: CharSequence)
  case class MakeBackup(filename: String)
  case class TxInquiry(txId: String)
  case object Terminate
  case class DownloadProgress(percentage: Float)
  case object HowMuchDownloaded
  case object WhatContents
  case class TxData(date: Date, depth: Int, hash: String, amount: BigInt)
  case class WalletContents(
    filename: String,
    isEncrypted: Boolean,
    addresses: List[String],
    availableBalance: BigInt,
    estimatedBalance: BigInt,
    transactions: List[TxData],
    unconfirmed: List[String]
  )
//  case object WhatTransactions
  type Error = Option[Exception]
  case object WhoArePeers
  case object HowManyPeers
  case object Replay
  case object AreStarted
}
