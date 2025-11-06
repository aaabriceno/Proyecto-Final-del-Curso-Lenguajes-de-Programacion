name := """lp-ecommerce"""
organization := "pe.ucsp.lp"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.17"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test
libraryDependencies ++= Seq(
  "org.xerial" % "sqlite-jdbc" % "3.45.3.0",
  "org.playframework.anorm" %% "anorm" % "2.7.0",
  "org.mindrot" % "jbcrypt" % "0.4" // passwords
)

libraryDependencies += "org.mindrot" % "jbcrypt" % "0.4"


// Adds additional packages into Twirl
//TwirlKeys.templateImports += "pe.ucsp.lp.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "pe.ucsp.lp.binders._"
