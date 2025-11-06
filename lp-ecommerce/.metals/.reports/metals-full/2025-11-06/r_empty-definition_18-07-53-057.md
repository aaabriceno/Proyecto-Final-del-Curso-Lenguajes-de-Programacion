error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/build-manual.sbt:
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/build-manual.sbt
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -baseDirectory.
	 -scala/Predef.baseDirectory.
offset: 345
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/build-manual.sbt
text:
```scala
name := "lp-ecommerce-manual"

version := "1.0"

scalaVersion := "2.13.12"

// NO dependencias externas
// Todo con Scala stdlib y java.net.*

// Configuración de compilación
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint"
)

// Directorio de código fuente
Compile / scalaSource := baseDirecto@@ry.value / "src"

// Main class
Compile / mainClass := Some("Main")

```


#### Short summary: 

empty definition using pc, found symbol in pc: 