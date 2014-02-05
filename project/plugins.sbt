import sbt._

import Defaults._

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.3.0")

libraryDependencies += sbtPluginExtra(
    m = "com.typesafe.sbt" % "sbt-start-script" % "0.10.0",
    sbtV = "0.13",
    scalaV = "2.10"
)
