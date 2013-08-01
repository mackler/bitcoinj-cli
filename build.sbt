name := "Bitcoinj-CLI"

version := "0.1.0"

scalaVersion := "2.10.2"

scalacOptions in Compile ++= Seq("-deprecation","-feature","-language:implicitConversions","-unchecked")

resolvers ++= Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"
)

libraryDependencies ++= Seq (
  "com.typesafe.akka" %% "akka-actor" % "2.2.0",
  "com.typesafe.akka" % "akka-slf4j_2.10" % "2.2.0",
  "ch.qos.logback" % "logback-classic" % "1.0.13",
  "org.scala-lang.virtualized" % "jline" % "2.10.2-RC1",
  "org.clapper" % "grizzled-slf4j_2.10" % "1.0.1",
  "com.google" % "bitcoinj" % "0.10",
  "com.frugalmechanic" % "scala-optparse_2.10" % "1.1.1"
)

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage := "org.mackler.bitcoincli"