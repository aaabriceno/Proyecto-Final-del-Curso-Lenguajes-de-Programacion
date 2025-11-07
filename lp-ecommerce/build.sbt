name := "lp-ecommerce"

version := "1.0"

scalaVersion := "2.13.12"

// Sin dependencias de frameworks - Scala puro
libraryDependencies ++= Seq(
  // Opcional: ScalaTest para tests futuros
  "org.scalatest" %% "scalatest" % "3.2.17" % Test
)

// Directorios de código fuente
Compile / scalaSource := baseDirectory.value / "app"
Compile / resourceDirectory := baseDirectory.value / "public"

// Punto de entrada principal
Compile / mainClass := Some("services.Main")

// Configuración del compilador
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint"
)

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "2.3.0"

