package org.mackler.bitcoincli

import com.google.bitcoin.core.PeerAddress
import java.net.{InetAddress,InetSocketAddress}

private object Discovery extends com.google.bitcoin.net.discovery.PeerDiscovery {

  private val addresses: Array[Tuple4[Int,Int,Int,Int]] = Array(
    // Got these by nslookup testnet-seed.bitcoin.petertodd.org
    (104,131,126,235),
    (106,186,125,121),
    (107,170,32,58),
    (162,243,165,105),
    // nslookup lookup testnet-seed.bluematt.me
    (162,243,132,6),
    // testnet-seed.bitcoin.schildbach.de
    (106,185,24,157),
    (109,201,135,216),
    (144,76,46,66),
    (144,76,175,228),
    (173,255,216,55),
    (188,165,246,217),
    (192,95,30,153),
    (192,99,9,6),
    (192,241,225,155),
    (195,154,69,36),
    (198,199,115,42),
    (5,39,85,97),
    (23,97,210,116),
    (46,4,28,150),
    (54,72,131,178),
    (54,83,21,194),
    (54,84,19,8),
    (54,206,106,94),
    (54,237,211,7),
    (78,46,18,137),
    (85,17,26,225),
    (88,198,20,152),
    (91,121,14,45),
    (93,93,135,12),
    (95,85,39,28)
  )

  def peerAddresses: Array[PeerAddress] = addresses.map ( a =>
      new PeerAddress ( new InetSocketAddress (
        InetAddress.getByAddress(Array[Byte](a._1.toByte, a._2.toByte, a._3.toByte, a._4.toByte)),
	18333
      ))
    )


  def getPeers(
    timeoutValue: Long, 
    timeoutUnit: java.util.concurrent.TimeUnit 
  ): Array[InetSocketAddress] =
    addresses.map ( a =>
      new InetSocketAddress (
        InetAddress.getByAddress(Array[Byte](a._1.toByte, a._2.toByte, a._3.toByte, a._4.toByte)),
	18333
      )
    )

  def shutdown() {}

}
