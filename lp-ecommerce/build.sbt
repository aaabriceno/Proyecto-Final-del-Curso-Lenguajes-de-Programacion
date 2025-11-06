name := "lp-ecommerce-manual"

organization := "pe.ucsp.lp"

version := "1.0-SNAPSHOT"

scalaVersion := "2.13.17"

// SIN FRAMEWORKS - Solo Scala stdlib
// NO Play, NO http4s, NO Akka HTTP
// Solo java.net.* para HTTP

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint"
)

// Configuración de directorios
Compile / scalaSource := baseDirectory.value / "app"
Compile / mainClass := Some("Main")

// Excluir carpetas viejas de la compilación
Compile / unmanagedSourceDirectories := Seq(
  baseDirectory.value / "app"
)

Compile / excludeFilter := HiddenFileFilter || "controllers_old" || "*.old"
