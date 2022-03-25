ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

lazy val ProjectName = "KM8"
lazy val ProjectOrganization = "KM8S"
lazy val ProjectVersion = "0.2.0-SNAPSHOT"
lazy val ProjectScalaVersion = "3.1.1"

lazy val zioVersion = "1.0.13"
lazy val zioKafkaVersion = "0.17.1"
lazy val zioJsonVersion = "0.2.0-M3"
lazy val zioLoggingVersion = "0.5.14"
lazy val zioPreludeVersion = "1.0.0-RC8"
lazy val kafkaVersion = "2.8.0"
lazy val kafkaProtobufVersion = "6.2.0"
lazy val javaFxVersion = "16"
lazy val scalaFxVersion = "16.0.0-R25"
lazy val osLibVersion = "0.8.0"
lazy val circeVersion = "0.14.1"
lazy val sttpVersion = "3.3.18"
lazy val logbackVersion = "1.2.11"
lazy val logstashVersion = "7.0.1"

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
    "org.openjfx" % s"javafx-$m" % javaFxVersion classifier osName
  )
}

lazy val fx = project
  .in(file("fx"))
  .settings(sharedSettings)
  .settings(
    name := "KafkaM8",
    libraryDependencies ++= Seq(
      "org.scalafx"                   %% "scalafx"                       % scalaFxVersion,
      "com.softwaremill.sttp.client3" %% "core"                          % sttpVersion,
      "com.softwaremill.sttp.client3" %% "httpclient-backend-zio"        % sttpVersion,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % sttpVersion
    ) ++ javaFXModules,
    Compile / run / mainClass := Some("io.km8.fx.Main")
  )
  .dependsOn(common)

lazy val core = project
  .in(file("core"))
  .settings(sharedSettings)
  .settings(
    name := "km8-core",
    libraryDependencies ++= Seq(
      "dev.zio"             %% "zio-kafka"                 % zioKafkaVersion,
      "dev.zio"             %% "zio-json"                  % zioJsonVersion,
      "dev.zio"             %% "zio-logging-slf4j"         % zioLoggingVersion,
      "io.confluent"         % "kafka-protobuf-serializer" % kafkaProtobufVersion,
      "com.lihaoyi"         %% "os-lib"                    % osLibVersion,
      "net.logstash.logback" % "logstash-logback-encoder"  % logstashVersion,
      "ch.qos.logback"       % "logback-classic"           % logbackVersion
    ),
    dependencyOverrides ++= Seq(
      "org.apache.kafka" % "kafka-clients" % kafkaVersion
    ),
    resolvers ++= Seq(
      "Confluent" at "https://packages.confluent.io/maven/"
    )
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
    "dev.zio"  %% "zio"           % zioVersion,
    "dev.zio"  %% "zio-prelude"   % zioPreludeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "dev.zio"  %% "zio-test"      % zioVersion % Test,
    "dev.zio"  %% "zio-test-sbt"  % zioVersion % Test
  ),
  testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
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
