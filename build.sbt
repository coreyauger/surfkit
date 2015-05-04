name := """surfkit"""

version := "1.0-SNAPSHOT"

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

lazy val commonSettings = Seq(
    organization := "im.surfkit",
    version := "1.0-SNAPSHOT",
    name := "surfkit",
    scalaVersion := "2.11.6"
  )


lazy val clientSettings = commonSettings ++ Seq(
  name := s"$name-client",
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
    name := s"$name-web",
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
    "com.github.mauricio"         %% "postgresql-async"    % "0.2.15",
    "org.scalatestplus"           %% "play"                % "1.2.0" % "test",
    "org.webjars"                 % "bootstrap"            % "3.3.4",
    "org.webjars"                 % "jquery"               % "2.1.4",
    "org.webjars"                 % "react"                % "0.13.1"
)


lazy val coreSettings = commonSettings ++ Seq(
  name := s"$name-core",
  libraryDependencies ++= coreDeps
)


lazy val coreDeps = {
  val akkaV = "2.3.9"
  val akkaStreamV = "1.0-M5"
  val scalaTestV = "2.2.1"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-core-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-testkit-experimental" % akkaStreamV,
    "org.scalatest" %% "scalatest" % scalaTestV % "test"
  )
}


