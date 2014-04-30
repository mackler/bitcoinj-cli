import com.typesafe.sbt.SbtStartScript

seq(SbtStartScript.startScriptForClassesSettings: _*)

name := "Bitcoinj-CLI"

version := "0.1.2"

scalaVersion := "2.10.2"

scalacOptions in Compile ++= Seq("-deprecation","-feature","-language:implicitConversions","-unchecked")

libraryDependencies ++= Seq (
  "com.typesafe.akka"         %% "akka-actor"          % "2.3.2",
  "com.typesafe.akka"          % "akka-slf4j_2.10"     % "2.3.2",
  "ch.qos.logback"             % "logback-classic"     % "1.1.2",
  "org.scala-lang.virtualized" % "jline"               % "2.10.2",
  "com.google"                 % "bitcoinj"            % "0.11.2",
  "com.frugalmechanic"         % "scala-optparse_2.10" % "1.1.1"
)

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage := "org.mackler.bitcoincli"
