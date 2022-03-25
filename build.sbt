ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

lazy val ProjectName = "KM8"
lazy val ProjectOrganization = "KM8S"
lazy val ProjectVersion = "0.2.0-SNAPSHOT"
lazy val ProjectScalaVersion = "3.1.1"

lazy val Versions = new {

  val zio = "1.0.13"
  val zioKafka = "0.17.3"
  val zioJson = "0.2.0-M3"
  val zioLogging = "0.5.14"
  val zioPrelude = "1.0.0-RC8"

  val kafka = "2.8.0"
  val kafkaProtobuf = "6.2.0"
  val javaFx = "16"
  val scalaFx = "16.0.0-R25"
  val osLib = "0.8.0"
  val circe = "0.14.1"
  val sttp = "3.3.18"
  val logback = "1.2.11"
  val logstash = "7.0.1"

  val testContainers = "0.40.2"
}

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val root = project
  .in(file("."))
  .aggregate(fx, common, core)
  .settings(
    name         := ProjectName,
    organization := ProjectOrganization,
    version      := ProjectVersion
  )
  .enablePlugins(DockerPlugin)
  .disablePlugins(RevolverPlugin)
  .settings(
    docker := (docker dependsOn (core / assembly)).value,
    docker / dockerfile := {
      val artifact: File = (core / assembly / assemblyOutputPath).value
      val artifactTargetPath = s"/app/${artifact.name}"

      new Dockerfile {
        from("openjdk:8-jre")

        env("KAFKAMATE_ENV", "prod")
        expose(8080, 61234, 61235)

        runRaw(
          "apt-get update && apt-get install -y dumb-init apt-transport-https ca-certificates curl gnupg2 software-properties-common"
        )
        runRaw("""curl -sL 'https://getenvoy.io/gpg' | apt-key add -""")
        runRaw("""apt-key fingerprint 6FF974DB | grep "5270 CEAC" """)
        runRaw(
          """add-apt-repository "deb [arch=amd64] https://dl.bintray.com/tetrate/getenvoy-deb $(lsb_release -cs) stable" """
        )
        runRaw("apt-get update && apt-get install -y getenvoy-envoy=1.15.1.p0.g670a4a6-1p69.ga5345f6")

        add(artifact, artifactTargetPath)
        copy(baseDirectory(_ / "site" / "build").value, "/usr/share/nginx/html/")

        entryPoint("/usr/bin/dumb-init", "--")
        cmd("./start.sh", artifactTargetPath)
      }
    },
    docker / imageNames := Seq(
      ImageName(s"${organization.value}/${name.value}:latest"),
      ImageName(
        repository = s"${organization.value}/${name.value}",
        tag = Some(version.value)
      )
    )
  )
  .settings(
    addCommandAlias("dockerize", ";compile;test;build;docker"),
    addCommandAlias("fmt", "all scalafmtSbt scalafmtAll test:scalafmt"),
    addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
  )

lazy val javaFXModules = {
  // Determine OS version of JavaFX binaries
  lazy val osName = System.getProperty("os.name") match {
    case n if n.startsWith("Linux")   => "linux"
    case n if n.startsWith("Mac")     => "mac"
    case n if n.startsWith("Windows") => "win"
    case _                            => throw new Exception("Unknown platform!")
  }
  Seq("base", "controls", "fxml", "graphics", "media", "swing", "web").map(m =>
    "org.openjfx" % s"javafx-$m" % Versions.javaFx classifier osName
  )
}

lazy val fx = project
  .in(file("fx"))
  .settings(sharedSettings)
  .settings(
    name := "KM8",
    libraryDependencies ++= Seq(
      "org.scalafx"                   %% "scalafx"                       % Versions.scalaFx,
      "com.softwaremill.sttp.client3" %% "core"                          % Versions.sttp,
      "com.softwaremill.sttp.client3" %% "httpclient-backend-zio"        % Versions.sttp,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % Versions.sttp
    ) ++ javaFXModules,
    Compile / run / mainClass := Some("io.km8.fx.Main"),
    assembly / assemblyMergeStrategy := {
      case x if x endsWith "io.netty.versions.properties" => MergeStrategy.discard
      case x if x endsWith "module-info.class"            => MergeStrategy.discard
      case x if x endsWith ".proto"                       => MergeStrategy.discard
      case x if x startsWith "org/reactivestreams/"       => MergeStrategy.first
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    assembly / test := {}
  )
  .dependsOn(common)

lazy val core = project
  .in(file("core"))
  .configs(IntegrationTest)
  .settings(sharedSettings)
  .settings(
    name := "km8-core",
    libraryDependencies ++= Seq(
      "dev.zio"             %% "zio-kafka"                  % Versions.zioKafka,
      "dev.zio"             %% "zio-json"                   % Versions.zioJson,
      "dev.zio"             %% "zio-logging-slf4j"          % Versions.zioLogging,
      "io.confluent"         % "kafka-protobuf-serializer"  % Versions.kafkaProtobuf,
      "com.lihaoyi"         %% "os-lib"                     % Versions.osLib,
      "net.logstash.logback" % "logstash-logback-encoder"   % Versions.logstash,
      "ch.qos.logback"       % "logback-classic"            % Versions.logback,
      "dev.zio"             %% "zio-test"                   % Versions.zio            % IntegrationTest,
      "com.dimafeng"        %% "testcontainers-scala-kafka" % Versions.testContainers % IntegrationTest
    ),
    dependencyOverrides ++= Seq(
      "org.apache.kafka" % "kafka-clients" % Versions.kafka
    ),
    resolvers ++= Seq(
      "Confluent" at "https://packages.confluent.io/maven/"
    ),
    Defaults.itSettings
  )
  .dependsOn(common)

lazy val common = project
  .in(file("common"))
  .settings(sharedSettings)
  .disablePlugins(RevolverPlugin)

lazy val sharedSettings = Seq(
  version      := ProjectVersion,
  scalaVersion := ProjectScalaVersion,
  scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-encoding",
    "utf8"
  ),
  libraryDependencies ++= Seq(
    "dev.zio"  %% "zio"           % Versions.zio,
    "dev.zio"  %% "zio-prelude"   % Versions.zioPrelude,
    "io.circe" %% "circe-generic" % Versions.circe,
    "dev.zio"  %% "zio-test"      % Versions.zio % Test,
    "dev.zio"  %% "zio-test-sbt"  % Versions.zio % Test
  ),
  testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
)
