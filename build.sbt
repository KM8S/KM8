ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

lazy val ProjectName = "kafkamate"
lazy val ProjectOrganization = "cipriansofronia"
lazy val ProjectVersion = "0.1.0"
lazy val ProjectScalaVersion = "2.13.3"

lazy val ZIOVersion  = "1.0.3"
lazy val GrpcVersion = "1.31.1"
lazy val SlinkyVersion = "0.6.6"

lazy val root = project
  .in(file("."))
  .aggregate(service, site)
  .settings(
    name := ProjectName,
    organization := ProjectOrganization,
    version := ProjectVersion
  )
  .enablePlugins(DockerPlugin)
  .settings(
    docker := (docker dependsOn (assembly in service)).value,
    dockerfile in docker := {
      val artifact: File = (assemblyOutputPath in assembly in service).value
      val artifactTargetPath = s"/app/${artifact.name}"

      new Dockerfile {
        from("openjdk:8-jre")
        maintainer("Ciprian Sofronia", "ciprian.sofronia@gmail.com")

        expose(8080, 8081, 9000)

        runRaw("apt-get update")
        runRaw("apt-get install -y dumb-init nginx nodejs apt-transport-https ca-certificates curl gnupg2 software-properties-common")
        runRaw("""curl -sL 'https://getenvoy.io/gpg' | apt-key add -""")
        runRaw("""apt-key fingerprint 6FF974DB | grep "5270 CEAC" """)
        runRaw("""add-apt-repository "deb [arch=amd64] https://dl.bintray.com/tetrate/getenvoy-deb $(lsb_release -cs) stable" """)
        runRaw("apt-get update")
        //runRaw("apt-cache policy getenvoy-envoy")
        runRaw("apt-get install -y getenvoy-envoy=1.15.1.p0.g670a4a6-1p69.ga5345f6")

        copy(baseDirectory(_ / "common" / "src" / "main" / "resources" / "envoy.yaml").value, "envoy.yaml")

        add(artifact, artifactTargetPath)

        runRaw("rm -v /etc/nginx/nginx.conf")
        copy(baseDirectory(_ / "site" / "conf").value, "/etc/nginx/")
        copy(baseDirectory(_ / "site" / "build").value, "/usr/share/nginx/html/")
        copy(baseDirectory(_ / "site" / "build").value, "/var/www/html/")

        copy(baseDirectory(_ / "start.sh" ).value, "start.sh")
        runRaw("chmod +x start.sh")

        entryPoint("/usr/bin/dumb-init", "--")
        cmd("./start.sh", artifactTargetPath)
      }
    },
    imageNames in docker := Seq(
      ImageName(s"${organization.value}/${name.value}:latest"),
      ImageName(
        repository = s"${organization.value}/${name.value}",
        tag = Some(version.value)
      )
    )
  )
  .settings(
    addCommandAlias("dockerize", ";compile;build;docker")
  )

lazy val service = project
  .in(file("service"))
  .settings(sharedSettings)
  .settings(
    name := "kafkamate-service",
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-encoding", "utf8",
      "-target:jvm-1.8",
      "-feature",
      "-language:_",
      "-Ywarn-dead-code",
      "-Ywarn-macros:after",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-Xlint",
      //"-Xfatal-warnings",,
      "-Xlint:-byname-implicit",
      "-Xlog-reflective-calls"
    ),
    libraryDependencies ++= Seq(
      "dev.zio"                         %% "zio-kafka"                          % "0.13.0+2-7ce016c2",
      "dev.zio"                         %% "zio-json"                           % "0.0.1",
      "com.lihaoyi"                     %% "os-lib"                             % "0.7.1",
      "com.thesamet.scalapb"            %% "scalapb-runtime-grpc"               % scalapb.compiler.Version.scalapbVersion,
      "io.grpc"                         %  "grpc-netty"                         % GrpcVersion,
      "com.fasterxml.jackson.module"    %% "jackson-module-scala"               % "2.10.0",
      "com.github.mlangc"               %% "slf4zio"                            % "1.0.0",
      "net.logstash.logback"            %  "logstash-logback-encoder"           % "6.3",
      "ch.qos.logback"                  %  "logback-classic"                    % "1.2.3",
      "io.github.embeddedkafka"         %% "embedded-kafka"                     % "2.6.0" % Test
    ),
    PB.targets in Compile := Seq(
      scalapb.gen(grpc = true) -> (sourceManaged in Compile).value,
      scalapb.zio_grpc.ZioCodeGenerator -> (sourceManaged in Compile).value,
    )
  )
  .dependsOn(common.jvm)
  .settings(
    assemblyMergeStrategy in assembly := {
      case x if x.endsWith("io.netty.versions.properties") => MergeStrategy.concat
      case "module-info.class" => MergeStrategy.discard
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
    test in assembly := {} //todo remove this
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
    version in webpack := "4.43.0",
    version in startWebpackDevServer:= "3.11.0",
    libraryDependencies ++= Seq(
      "me.shadaj" %%% "slinky-core" % SlinkyVersion,
      "me.shadaj" %%% "slinky-web" % SlinkyVersion,
      "me.shadaj" %%% "slinky-native" % SlinkyVersion,
      "me.shadaj" %%% "slinky-hot" % SlinkyVersion,
      "me.shadaj" %%% "slinky-react-router" % SlinkyVersion,
      "me.shadaj" %%% "slinky-scalajsreact-interop" % SlinkyVersion,
      //"com.github.oen9" %%% "slinky-bridge-react-konva" % "0.1.1",
      "org.scalatest" %%% "scalatest" % "3.1.1" % Test
    ),
    npmDependencies in Compile ++= Seq(
      "react"            -> "16.13.1",
      "react-dom"        -> "16.13.1",
      "react-proxy"      -> "1.1.8",
      "react-router-dom" -> "5.2.0",
      "path-to-regexp"   -> "3.0.0",
      //"react-konva"      -> "16.13.0-3",
      //"konva"            -> "4.2.2",
      "use-image"        -> "1.0.6"
    ),
    npmDevDependencies in Compile ++= Seq(
      "file-loader" -> "6.0.0",
      "style-loader" -> "1.2.1",
      "css-loader" -> "3.5.3",
      "html-webpack-plugin" -> "4.3.0",
      "copy-webpack-plugin" -> "5.1.1",
      "webpack-merge" -> "4.2.2"
    ),
    webpackResources := baseDirectory.value / "webpack" * "*",
    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "webpack" / "webpack-fastopt.config.js"),
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack" / "webpack-opt.config.js"),
    webpackConfigFile in Test := Some(baseDirectory.value / "webpack" / "webpack-core.config.js"),
    webpackDevServerExtraArgs in fastOptJS := Seq("--inline", "--hot"),
    webpackBundlingMode in fastOptJS := BundlingMode.LibraryOnly(),
    requireJsDomEnv in Test := true,
    addCommandAlias("dev", ";fastOptJS::startWebpackDevServer;~fastOptJS"),
    addCommandAlias("build", "fullOptJS::webpack"),
    test in Compile := {} //disable site tests for now
  )
  .dependsOn(common.js)

lazy val common = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("common"))
  .settings(sharedSettings)
  .disablePlugins(RevolverPlugin)
  .settings(
    libraryDependencies += "com.thesamet.scalapb" %%% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion,
    PB.protoSources in Compile := Seq(
      (baseDirectory in ThisBuild).value / "common" / "src" / "main" / "protobuf"
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb"  %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "io.grpc"               %  "grpc-netty"           % GrpcVersion
    ),
    PB.targets in Compile := Seq(
      scalapb.gen(grpc = true) -> (sourceManaged in Compile).value,
      scalapb.zio_grpc.ZioCodeGenerator -> (sourceManaged in Compile).value,
    )
  )
  .jsSettings(
    // publish locally and update the version for test
    libraryDependencies += "com.thesamet.scalapb.grpcweb" %%% "scalapb-grpcweb" % scalapb.grpcweb.BuildInfo.version,
    PB.targets in Compile := Seq(
      scalapb.gen(grpc = false) -> (sourceManaged in Compile).value,
      scalapb.grpcweb.GrpcWebCodeGenerator -> (sourceManaged in Compile).value
    )
  )

lazy val sharedSettings = Seq(
  version := ProjectVersion,
  scalaVersion := ProjectScalaVersion,
  scalacOptions ++= Seq(
    "-Ymacro-annotations"
  ),
  libraryDependencies ++= Seq(
    "dev.zio"                         %%% "zio"                                % ZIOVersion,
    "dev.zio"                         %%% "zio-macros"                         % ZIOVersion,
    "io.circe"                        %%% "circe-generic"                      % "0.13.0",
    "dev.zio"                         %%% "zio-test"                           % ZIOVersion % Test,
    "dev.zio"                         %%% "zio-test-sbt"                       % ZIOVersion % Test,
  ),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full),
  testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  //,bloopExportJarClassifiers in Global := Some(Set("sources"))
)