name := "lp-ecommerce"

version := "1.0"

scalaVersion := "2.13.12"

// Sin dependencias de frameworks - Scala puro
libraryDependencies ++= Seq(
  // MongoDB driver oficial para Scala
  "org.mongodb.scala" %% "mongo-scala-driver" % "4.11.1",
  
  // JSON para serialización
  "io.circe" %% "circe-core" % "0.14.6",
  "io.circe" %% "circe-generic" % "0.14.6",
  "io.circe" %% "circe-parser" % "0.14.6",
  
  // Opcional: ScalaTest para tests futuros
  "org.scalatest" %% "scalatest" % "3.2.17" % Test,

  // Generación de PDFs desde HTML
  "com.openhtmltopdf" % "openhtmltopdf-pdfbox" % "1.0.10",

  // Generación de códigos QR
  "com.google.zxing" % "core" % "3.5.3",
  "com.google.zxing" % "javase" % "3.5.3",

  // Envío de correos (Jakarta Mail)
  "com.sun.mail" % "jakarta.mail" % "2.0.1"
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

