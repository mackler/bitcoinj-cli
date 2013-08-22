package org.mackler.bitcoincli

import java.net.{InetAddress,InetSocketAddress}
private object Discovery extends com.google.bitcoin.discovery.PeerDiscovery {

  private val addresses: Array[Tuple4[Int,Int,Int,Int]] = Array(
    // Got these by nslookup testnet-seed.bitcoin.petertodd.org
    (77,111,9,2),
    (185,19,105,27),
    (198,50,215,81),
    (199,231,188,248),
    (178,63,48,141),
    (5,135,188,39),
    (198,199,70,246),
    (5,9,2,145),
    (89,31,97,13),
    (95,211,60,21),
    (176,9,24,110),
    (213,5,71,38),
    (5,200,9,230),
    (193,183,98,102),
    (88,198,39,134),
    (178,209,44,70),
    (188,40,51,211),
    (199,231,187,226),
    (199,19,108,71),
    (106,187,52,100),
    (144,76,46,66)
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
