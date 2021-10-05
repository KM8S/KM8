ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

lazy val ProjectName         = "kafkamate"
lazy val ProjectOrganization = "csofronia"
lazy val ProjectVersion      = "0.1.1"
lazy val ProjectScalaVersion = "2.13.6"

lazy val Versions = new {
  val zio  = "1.0.9"
  val grpc = "1.41.0"
}

lazy val SlinkyVersion = "0.6.7"

lazy val kafkamate = project
  .in(file("."))
  .aggregate(service, site)
  .settings(
    name := ProjectName,
    organization := ProjectOrganization,
    version := ProjectVersion
  )
  .enablePlugins(DockerPlugin)
  .disablePlugins(RevolverPlugin)
  .settings(
    docker := (docker dependsOn (service / assembly)).value,
    docker / dockerfile := {
      val artifact: File     = (service / assembly / assemblyOutputPath).value
      val artifactTargetPath = s"/app/${artifact.name}"

      new Dockerfile {
        from("openjdk:8-jre")
        maintainer("Ciprian Sofronia", "ciprian.sofronia@gmail.com")

        env("KAFKAMATE_ENV", "prod")
        expose(8080, 61234, 61235)

        runRaw(
          "apt-get update && apt-get mb-init nginx nodejs apt-transport-https ca-certificates curl gnupg2 software-properties-common"
        )
        runRaw("""curl -sL 'https://getenvoy.io/gpg' | apt-key add -""")
        runRaw("""apt-key fingerprint 6FF974DB | grep "5270 CEAC" """)
        runRaw(
          """add-apt-repository "deb [arch=amd64] https://dl.bintray.com/tetrate/getenvoy-deb $(lsb_release -cs) stable" """
        )
        runRaw("apt-get update && apt-get install -y getenvoy-envoy=1.15.1.p0.g670a4a6-1p69.ga5345f6")

        runRaw("rm -v /etc/nginx/nginx.conf")
        copy(baseDirectory(_ / "build" / "nginx").value, "/etc/nginx/")
        copy(baseDirectory(_ / "build" / "envoy.yaml").value, "envoy.yaml")
        copy(baseDirectory(_ / "build" / "start.sh").value, "start.sh")

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

lazy val service = project
  .in(file("service"))
  .settings(sharedSettings)
  .settings(
    name := "kafkamate-service",
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-encoding",
      "utf8",
      "-target:jvm-1.8",
      "-feature",
      "-language:_",
      "-Ywarn-dead-code",
      "-Ywarn-macros:after",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-Xlint",
      //"-Xfatal-warnings",
      "-Xlint:-byname-implicit",
      "-Xlog-reflective-calls"
    ),
    libraryDependencies ++= Seq(
      "dev.zio"              %% "zio-kafka"                 % "0.15.0",
      "dev.zio"              %% "zio-json"                  % "0.1.5",
      "dev.zio"              %% "zio-logging-slf4j"         % "0.5.11",
      "com.lihaoyi"          %% "os-lib"                    % "0.7.8",
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc"      % scalapb.compiler.Version.scalapbVersion,
      "io.confluent"          % "kafka-protobuf-serializer" % "6.2.0",
      //"com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.10.0",
      "net.logstash.logback"     % "logstash-logback-encoder" % "6.6",
      "ch.qos.logback"           % "logback-classic"          % "1.2.3",
      "io.github.embeddedkafka" %% "embedded-kafka"           % "2.8.0" % Test
    ),
    dependencyOverrides ++= Seq(
      "org.apache.kafka" % "kafka-clients" % "2.8.0"
    ),
    resolvers ++= Seq(
      "Confluent" at "https://packages.confluent.io/maven/"
    ),
    Compile / PB.targets := Seq(
      scalapb.gen(grpc = true)          -> (Compile / sourceManaged).value,
      scalapb.zio_grpc.ZioCodeGenerator -> (Compile / sourceManaged).value
    )
  )
  .dependsOn(common.jvm)
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

lazy val site = project
  .in(file("site"))
  .enablePlugins(ScalaJSBundlerPlugin)
  .disablePlugins(RevolverPlugin)
  .settings(sharedSettings)
  .settings(
    name := "kafkamate-site",
    scalacOptions ++= {
      if (scalaJSVersion.startsWith("0.6.")) Seq("-P:scalajs:sjsDefinedByDefault")
      else Nil
    },
    webpack / version := "4.43.0",
    startWebpackDevServer / version := "3.11.0",
    libraryDependencies ++= Seq(
      "me.shadaj"     %%% "slinky-core"                 % SlinkyVersion,
      "me.shadaj"     %%% "slinky-web"                  % SlinkyVersion,
      "me.shadaj"     %%% "slinky-native"               % SlinkyVersion,
      "me.shadaj"     %%% "slinky-hot"                  % SlinkyVersion,
      "me.shadaj"     %%% "slinky-react-router"         % SlinkyVersion,
      "me.shadaj"     %%% "slinky-scalajsreact-interop" % SlinkyVersion,
      "org.scalatest" %%% "scalatest"                   % "3.2.9" % Test
      //"com.github.oen9" %%% "slinky-bridge-react-konva"   % "0.1.1",
    ),
    Compile / npmDependencies ++= Seq(
      "react"            -> "16.13.1",
      "react-dom"        -> "16.13.1",
      "react-proxy"      -> "1.1.8",
      "react-router-dom" -> "5.2.0",
      "path-to-regexp"   -> "3.0.0",
      "use-image"        -> "1.0.6"
      //"react-konva"      -> "16.13.0-3",
      //"konva"            -> "4.2.2",
    ),
    Compile / npmDevDependencies ++= Seq(
      "file-loader"         -> "6.0.0",
      "style-loader"        -> "1.2.1",
      "css-loader"          -> "3.5.3",
      "html-webpack-plugin" -> "4.3.0",
      "copy-webpack-plugin" -> "5.1.1",
      "webpack-merge"       -> "4.2.2"
    ),
    webpackResources := baseDirectory.value / "webpack" * "*",
    fastOptJS / webpackConfigFile := Some(baseDirectory.value / "webpack" / "webpack-fastopt.config.js"),
    fullOptJS / webpackConfigFile := Some(baseDirectory.value / "webpack" / "webpack-opt.config.js"),
    Test / webpackConfigFile := Some(baseDirectory.value / "webpack" / "webpack-core.config.js"),
    fastOptJS / webpackDevServerExtraArgs := Seq("--inline", "--hot"),
    fastOptJS / webpackBundlingMode := BundlingMode.LibraryOnly(),
    Test / requireJsDomEnv := true,
    addCommandAlias("dev", ";fastOptJS::startWebpackDevServer;~fastOptJS"),
    addCommandAlias("build", "fullOptJS::webpack"),
    Compile / test := {} //disable site tests for now
  )
  .dependsOn(common.js)

lazy val common = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("common"))
  .settings(sharedSettings)
  .disablePlugins(RevolverPlugin)
  .settings(
    libraryDependencies += "com.thesamet.scalapb" %%% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion,
    Compile / PB.protoSources := Seq(
      (ThisBuild / baseDirectory).value / "common" / "src" / "main" / "protobuf"
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "io.grpc"               % "grpc-netty"           % Versions.grpc
    ),
    Compile / PB.targets := Seq(
      scalapb.gen(grpc = true)          -> (Compile / sourceManaged).value,
      scalapb.zio_grpc.ZioCodeGenerator -> (Compile / sourceManaged).value
    )
  )
  .jsSettings(
    // publish locally and update the version for test
    libraryDependencies += "com.thesamet.scalapb.grpcweb" %%% "scalapb-grpcweb" % scalapb.grpcweb.BuildInfo.version,
    Compile / PB.targets := Seq(
      scalapb.gen(grpc = false)            -> (Compile / sourceManaged).value,
      scalapb.grpcweb.GrpcWebCodeGenerator -> (Compile / sourceManaged).value
    )
  )

lazy val sharedSettings = Seq(
  version := ProjectVersion,
  scalaVersion := ProjectScalaVersion,
  scalacOptions ++= Seq(
    "-Ymacro-annotations"
  ),
  libraryDependencies ++= Seq(
    "dev.zio"  %%% "zio"           % Versions.zio,
    "dev.zio"  %%% "zio-macros"    % Versions.zio,
    "io.circe" %%% "circe-generic" % "0.14.1",
    "dev.zio"  %%% "zio-test"      % Versions.zio % Test,
    "dev.zio"  %%% "zio-test-sbt"  % Versions.zio % Test
  ),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.0" cross CrossVersion.full),
  testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  //,bloopExportJarClassifiers in Global := Some(Set("sources"))
)
