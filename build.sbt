import com.typesafe.sbt.less.Import.LessKeys

import sbt.Keys._

import org.scalajs.sbtplugin.ScalaJSPlugin

import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport._

import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

import sbt._

import Keys._

packageDescription in Debian := "surfkit.im"

maintainer in Debian := "Corey Auger coreyauger@gmail.com"

name := """surfkit"""

version := "1.0-SNAPSHOT"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"


val surfkit = "surfkit"

val scalajsOutputDir = Def.settingKey[File]("directory for javascript files output by scalajs")

val sharedScalaDir = file(".") / "shared" / "main" / "scala"

val copySourceMapsTask = Def.task {
  val scalaFiles = (Seq(sharedScalaDir, client.base) ** "*.scala").get
  for (scalaFile <- scalaFiles) {
    val target = new File((classDirectory in Compile).value, scalaFile.getPath)
    IO.copyFile(scalaFile, target)
  }
}


//lazy val root = project.in(file(".")).enablePlugins(PlayScala)

lazy val root =
  (project in file("."))
  .settings(commonSettings:_*)
  .aggregate(core)
  .aggregate(web)
    .aggregate(hangten)

lazy val web =
  (project in file("web"))
	.settings(webSettings:_*)
  .settings(sharedDirSettings:_*)
	.enablePlugins(PlayScala) aggregate client

lazy val core =
  (project in file("core"))
    .settings(coreSettings:_*)
    .settings(sharedDirSettings:_*)

lazy val client =
  project.in(file("client"))
  .settings(clientSettings:_*)
  .enablePlugins(ScalaJSPlugin)

// Default modules ...
lazy val hangten =
  (project in file("modules/hangten"))
    .settings(moduleSettings:_*)
    .dependsOn(core)

lazy val commonSettings = Seq(
    organization := "im.surfkit",
    version := "1.0-SNAPSHOT",
    name := "surfkit",
    scalaVersion := "2.11.6"
  )


lazy val moduleSettings = Seq(
  scalaVersion := "2.11.6"
)

lazy val clientSettings = commonSettings ++ Seq(
  name := s"$surfkit-client",
  libraryDependencies ++= clientDeps.value
) ++ sharedDirSettings


lazy val clientDeps = Def.setting(Seq(
  "com.github.japgolly.scalajs-react" %%% "core" % "0.8.4",
  "com.github.japgolly.scalajs-react" %%% "test" % "0.8.4" % "test",
  "com.github.japgolly.scalajs-react" %%% "ext-scalaz71" % "0.8.4",
  "org.scala-js" %%% "scalajs-dom" % "0.8.0",
  "com.lihaoyi" %%% "scalatags" % "0.5.1",
  "com.lihaoyi" %%% "upickle" % "0.2.8",
  "be.doeraene" %%% "scalajs-jquery"  % "0.8.0"
))

lazy val webSettings = commonSettings ++ Seq(
    name := s"$surfkit-web",
  scalajsOutputDir := (classDirectory in Compile).value / "public" / "javascripts",
  compile in Compile <<= (compile in Compile) dependsOn (fastOptJS in (client, Compile)) dependsOn copySourceMapsTask,
  dist <<= dist dependsOn (fullOptJS in (client, Compile)),
  stage <<= stage dependsOn (fullOptJS in (client, Compile)),
  libraryDependencies ++= webDeps,
  includeFilter in (Assets, LessKeys.less) := "__main.less"
) ++ (
  // ask scalajs project to put its outputs in scalajsOutputDir
  Seq(fastOptJS, fullOptJS) map { packageJSKey =>
    crossTarget in (client, Compile, packageJSKey) := scalajsOutputDir.value
  }
  ) ++ sharedDirSettings

lazy val sharedDirSettings = Seq(
  unmanagedSourceDirectories in Compile +=
    baseDirectory.value / "shared" / "main" / "scala"
)

lazy val webDeps = Seq(
    ws,
    "com.github.mauricio"         %% "postgresql-async"    % "0.2.15",
    "ws.securesocial"             %% "securesocial"        % "3.0-M3",
    "org.scalatestplus"           %% "play"                % "1.2.0" % "test",
    "org.webjars"                 % "bootstrap"            % "3.3.4",
    "org.webjars"                 % "jquery"               % "2.1.4",
    "org.webjars"                 % "react"                % "0.13.1"
)


lazy val coreSettings = commonSettings ++ Seq(
  name := s"$surfkit-core",
  libraryDependencies ++= coreDeps
)


lazy val coreDeps = {
  val akkaV = "2.3.9"
  val sprayV = "1.3.3"
  val akkaStreamV = "1.0-M5"
  val scalaTestV = "2.2.1"
  Seq(
    ws,
    "com.typesafe.akka" %% "akka-actor"                         % akkaV,
    "com.typesafe.akka" %% "akka-stream-experimental"           % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-core-experimental"        % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-experimental"             % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental"  % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-testkit-experimental"     % akkaStreamV,
    "io.spray"          %%  "spray-can"                         % sprayV,
    "io.spray"          %%  "spray-routing-shapeless2"          % sprayV,
    "com.rabbitmq"       %  "amqp-client"                       % "3.3.5",
    "com.wandoulabs.akka" %%  "spray-websocket"                 % "0.1.4",
    "com.typesafe.play" %% "play-json"                          % "2.3.4",
    "org.scalatest"     %% "scalatest"                          % scalaTestV % "test"
  )
}





// debug
val exportFullResolvers = taskKey[Unit]("debug resolvers")

exportFullResolvers := {
  for {
    (resolver,idx) <- fullResolvers.value.zipWithIndex
  } println(s"${idx}.  ${resolver.name}")
}