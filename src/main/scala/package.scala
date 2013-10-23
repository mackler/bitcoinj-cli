package org.mackler {
  package object bitcoincli {

    type Outcome = Either[Exception,String]

    implicit def scalaBigInt2bigInteger(x: scala.math.BigInt): java.math.BigInteger = x.underlying
    implicit val j2bi = scala.math.BigInt.javaBigInteger2bigInt _

    // TODO: finish moving the message classes from Server.scala (and deleting unused ones)
    case object PasswordNeeded
    case class Payment(address: String, amount: BigInt, password: Option[String] = None)

  }
}
