package org.mackler {
  package object bitcoincli {
    implicit def scalaBigInt2bigInteger(x: scala.math.BigInt): java.math.BigInteger = x.underlying
  }
}
