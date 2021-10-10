lazy val Versions = new {
  val zio            = "2.0.0-M3"
  val zioLogging     = "0.5.12"
  val zioConfig      = "1.0.10"
  val zioJson        = "0.2.0-M1"
  val zioHttp        = "1.0.0.0-RC17"
  val logback        = "1.2.6"
  val testContainers = "0.39.8"
}

ThisBuild / scalaVersion := "3.1.0-RC2"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "io.km8"
ThisBuild / organizationName := "Kafka Mate"

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    name := "km8",
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    libraryDependencies ++= compileDependencies ++ testDependencies
  )
  .settings(Defaults.itSettings)

val compileDependencies = Seq(
  "dev.zio"       %% "zio"                 % Versions.zio,
  "dev.zio"       %% "zio-test-sbt"        % Versions.zio,
  "dev.zio"       %% "zio-logging-slf4j"   % Versions.zioLogging,
  "dev.zio"       %% "zio-config"          % Versions.zioConfig,
  "dev.zio"       %% "zio-config-typesafe" % Versions.zioConfig,
  "dev.zio"       %% "zio-json"            % Versions.zioJson,
  "dev.zio"       %% "zio-streams"         % Versions.zio,
  "io.d11"        %% "zhttp"               % Versions.zioHttp,
  "ch.qos.logback" % "logback-classic"     % Versions.logback
)

val testDependencies = Seq(
  "dev.zio"      %% "zio-test"                   % Versions.zio            % "test,it",
  "com.dimafeng" %% "testcontainers-scala-kafka" % Versions.testContainers % "test,it"
)
