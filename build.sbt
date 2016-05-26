name := """kiros-files"""

version := "1.0"

maintainer := "Alex Prudencio <alex.prudencio@gmail.com>"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-http-experimental" % "2.4.4",
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "2.4.4",
  "com.typesafe.akka" %% "akka-stream" % "2.4.4",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.4.4",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11" % "test",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "com.typesafe" % "config" % "1.3.0"
  )
  
resolvers ++= Seq(
    "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
    "RoundEights" at "http://maven.spikemark.net/roundeights"
)

enablePlugins(JavaServerAppPackaging)

lazy val core = RootProject(file("../kiros-commons"))
lazy val root = (project in file(".")).dependsOn(core)
