ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

lazy val ProjectName = "kafkamate"
lazy val ProjectOrganization = "csofronia"
lazy val ProjectVersion = "0.1.0"
lazy val ProjectScalaVersion = "3.1.0"

lazy val ZIOVersion = "1.0.12"
lazy val SlinkyVersion = "0.6.7"

val scalaFxVersion = "16.0.0-R25"
val zioPreludeVersion = "1.0.0-RC8"
val sttpVersion = "3.3.16"

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
        maintainer("Ciprian Sofronia", "ciprian.sofronia@gmail.com")

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
    addCommandAlias("dockerize", ";compile;test;build;docker")
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
    "org.openjfx" % s"javafx-$m" % "16" classifier osName
  )
}

lazy val fx = project
  .in(file("fx"))
  .settings(sharedSettings)
  .settings(
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
      "dev.zio"     %% "zio-kafka"                 % "0.16.0",
      "dev.zio"     %% "zio-json"                  % "0.2.0-M3",
      "dev.zio"     %% "zio-logging-slf4j"         % "0.5.14",
      "com.lihaoyi" %% "os-lib"                    % "0.7.8",
      "io.confluent" % "kafka-protobuf-serializer" % "6.2.0",
      // "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.10.0",
      "net.logstash.logback" % "logstash-logback-encoder" % "6.6",
      "ch.qos.logback"       % "logback-classic"          % "1.2.3"
      // "io.github.embeddedkafka" %% "embedded-kafka"           % "3.0.0" % Test
    ),
    dependencyOverrides ++= Seq(
      "org.apache.kafka" % "kafka-clients" % "2.8.0"
    ),
    resolvers ++= Seq(
      "Confluent" at "https://packages.confluent.io/maven/"
    )
  )
  .dependsOn(common)
  .settings(
    assembly / assemblyMergeStrategy := {
      case x if x endsWith "io.netty.versions.properties" => MergeStrategy.concat
      case x if x endsWith "module-info.class"            => MergeStrategy.discard
      case x if x endsWith ".proto"                       => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    assembly / test := {}
  )

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
//    "-feature",
//    "-verbose"
//    "-Ydebug",
//    "-language:_"
    // "-Xfatal-warnings",
  ),
  libraryDependencies ++= Seq(
    "dev.zio"  %% "zio"           % ZIOVersion,
    "dev.zio"  %% "zio-prelude"   % zioPreludeVersion,
    "io.circe" %% "circe-generic" % "0.14.1",
    "dev.zio"  %% "zio-test"      % ZIOVersion % Test,
    "dev.zio"  %% "zio-test-sbt"  % ZIOVersion % Test
  ),
  testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  // ,bloopExportJarClassifiers in Global := Some(Set("sources"))
)
