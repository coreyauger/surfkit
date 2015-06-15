import com.typesafe.sbt.less.Import.LessKeys

import sbt.Keys._

import org.scalajs.sbtplugin.ScalaJSPlugin

import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport._

import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

import sbt._

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
  //val scalaFiles = (Seq(sharedScalaDir, clientMax.base) ** "*.scala").get
  for (scalaFile <- scalaFiles) {
    val target = new File((classDirectory in Compile).value, scalaFile.getPath)
    IO.copyFile(scalaFile, target)
  }
}

lazy val root =
  (project in file("."))
  .settings(commonSettings:_*)
  .aggregate(core)
   // .aggregate(webMax)
  .aggregate(web)
    .aggregate(hangten)
    .aggregate(sexwax)

lazy val web =
  (project in file("web"))
	.settings(webSettings:_*)
  .settings(sharedDirSettings:_*)
	.enablePlugins(PlayScala) aggregate client


//lazy val webMax =
//  (project in file("web-max"))
//    .settings(webSettings:_*)
//    .settings(sharedDirSettings:_*)
//    .enablePlugins(PlayScala) aggregate clientMax

lazy val core =
  (project in file("core"))
    .settings(coreSettings:_*)
    .settings(sharedDirSettings:_*)

lazy val client =
  project.in(file("client"))
  .settings(clientSettings:_*)
  .enablePlugins(ScalaJSPlugin)


lazy val clientMax =
  project.in(file("client-max"))
    .settings(clientSettings:_*)
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(scalaJsMaps)

lazy val scalaJsMaps = uri("../scalajs-google-maps")

// Default modules ...
lazy val hangten =
  (project in file("modules/hangten"))
    .settings(moduleSettings:_*)
    .dependsOn(core)

lazy val sexwax =
  (project in file("modules/sexwax"))
    .settings(moduleSettings:_*)
    .dependsOn(core)

lazy val maxsearch =
  (project in file("modules/maxsearch"))
    .settings(moduleSettings:_*)
    .dependsOn(core)

lazy val commonSettings = Seq(
    organization := "im.surfkit",
    version := "1.0-SNAPSHOT",
    name := "surfkit",
    scalaVersion := "2.11.6"
  )


lazy val moduleSettings = Seq(
  scalaVersion := "2.11.6",
  libraryDependencies ++= hangTenDeps
)

lazy val clientSettings = commonSettings ++ Seq(
  name := s"$surfkit-client",
  libraryDependencies ++= clientDeps.value
) ++ sharedDirSettings


lazy val clientDeps = Def.setting(Seq(
  "com.github.japgolly.scalajs-react" %%% "core" % "0.8.4",
  "com.github.japgolly.scalajs-react" %%% "test" % "0.8.4" % "test",
  "com.github.japgolly.scalajs-react" %%% "ext-scalaz71" % "0.8.4",
  "com.github.japgolly.scalajs-react" %%% "extra" % "0.8.4",
  "org.scala-js" %%% "scalajs-dom" % "0.8.0",
  "com.lihaoyi" %%% "scalatags" % "0.5.1",
  "com.lihaoyi" %%% "upickle" % "0.2.8",
  "be.doeraene" %%% "scalajs-jquery"  % "0.8.0"
  //, "io.surfkit" %%% "scalajs-google-maps" % "0.1-SNAPSHOT"
))

lazy val webSettings = commonSettings ++ Seq(
  name := s"$surfkit-web",
  //name := s"max-web",
  scalajsOutputDir := (classDirectory in Compile).value / "public" / "javascripts",
  compile in Compile <<= (compile in Compile) dependsOn (fastOptJS in (client, Compile)) dependsOn copySourceMapsTask,
  dist <<= dist dependsOn (fullOptJS in (client, Compile)),
  stage <<= stage dependsOn (fullOptJS in (client, Compile)),
  //compile in Compile <<= (compile in Compile) dependsOn (fastOptJS in (clientMax, Compile)) dependsOn copySourceMapsTask,
  //dist <<= dist dependsOn (fullOptJS in (clientMax, Compile)),
  //stage <<= stage dependsOn (fullOptJS in (clientMax, Compile)),
  libraryDependencies ++= webDeps,
  includeFilter in (Assets, LessKeys.less) := "__main.less"
) ++ (
  // ask scalajs project to put its outputs in scalajsOutputDir
  Seq(fastOptJS, fullOptJS) map { packageJSKey =>
    crossTarget in (client, Compile, packageJSKey) := scalajsOutputDir.value
  }
  //Seq(fastOptJS, fullOptJS) map { packageJSKey =>
  //    crossTarget in (clientMax, Compile, packageJSKey) := scalajsOutputDir.value
  //  }
  ) ++ sharedDirSettings





lazy val sharedDirSettings = Seq(
  unmanagedSourceDirectories in Compile +=
    baseDirectory.value / "shared" / "main" / "scala"
)

lazy val hangTenDeps = Seq(
  "com.typesafe.slick"            %% "slick"            % "3.0.0",
  //"org.slf4j"                     % "slf4j-nop"         % "1.6.4",
  "postgresql"                    % "postgresql"        % "9.1-901.jdbc4"
)

lazy val webDeps = Seq(
    ws,
    "com.github.mauricio"         %% "postgresql-async"    % "0.2.15",
    "ws.securesocial"             %% "securesocial"        % "3.0-M3",
    "org.scalatestplus"           %% "play"                % "1.2.0" % "test",
    "com.lihaoyi"                 %% "upickle"             % "0.2.8",
    "org.webjars"                 % "bootstrap"            % "3.3.4",
    "org.webjars"                 % "jquery"               % "2.1.4",
    "org.webjars"                 % "react"                % "0.13.1",
    "org.webjars"                 % "react-router"         % "0.13.2"
)


lazy val coreSettings = commonSettings ++ Seq(
  name := s"$surfkit-core",
  libraryDependencies ++= coreDeps
)


lazy val coreDeps = {
  val akkaV = "2.3.9"
  val sprayV = "1.3.3"
  val akkaStreamV = "1.0-M5"
  //val akkaStreamV = "1.0-RC3" TODO: ...
  val scalaTestV = "2.2.1"
  Seq(
    ws,
    "com.typesafe.akka"   %% "akka-actor"                         % akkaV,
    "com.typesafe.akka"   %% "akka-stream-experimental"           % akkaStreamV,
    "com.typesafe.akka"   %% "akka-http-core-experimental"        % akkaStreamV,
    "com.typesafe.akka"   %% "akka-http-experimental"             % akkaStreamV,
    "com.typesafe.akka"   %% "akka-http-spray-json-experimental"  % akkaStreamV,
    "com.typesafe.akka"   %% "akka-http-testkit-experimental"     % akkaStreamV,
    "com.github.mauricio" %% "postgresql-async"                   % "0.2.15",
    "io.spray"            %%  "spray-can"                         % sprayV,
    "io.spray"            %%  "spray-routing-shapeless2"          % sprayV,
    "com.rabbitmq"        %  "amqp-client"                        % "3.3.5",
    "com.wandoulabs.akka" %%  "spray-websocket"                   % "0.1.4",
    "com.typesafe.play"   %% "play-json"                          % "2.3.4",
    "com.lihaoyi"         %% "upickle"                            % "0.2.8",
    "org.scalatest"       %% "scalatest"                          % scalaTestV % "test"
  )
}



// debug
val exportFullResolvers = taskKey[Unit]("debug resolvers")

exportFullResolvers := {
  for {
    (resolver,idx) <- fullResolvers.value.zipWithIndex
  } println(s"${idx}.  ${resolver.name}")
}