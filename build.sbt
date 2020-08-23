ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

lazy val ZIOVersion  = "1.0.1"
lazy val GrpcVersion = "1.31.1"

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
      //"-Xfatal-warnings",
      "-Xlog-reflective-calls"
    ),
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb"            %% "scalapb-runtime-grpc"               % scalapb.compiler.Version.scalapbVersion,
      "io.grpc"                         %  "grpc-netty"                         % GrpcVersion,
      "com.fasterxml.jackson.module"    %% "jackson-module-scala"               % "2.10.0",
      "com.github.mlangc"               %% "slf4zio"                            % "0.7.0",
      "net.logstash.logback"            %  "logstash-logback-encoder"           % "6.3",
      "ch.qos.logback"                  %  "logback-classic"                    % "1.2.3",
      "io.github.embeddedkafka"         %% "embedded-kafka"                     % "2.4.1" % Test
    ),
    PB.targets in Compile := Seq(
      scalapb.gen(grpc = true) -> (sourceManaged in Compile).value,
      scalapb.zio_grpc.ZioCodeGenerator -> (sourceManaged in Compile).value,
    )
  )
  .dependsOn(common.jvm)

lazy val site = project
  .in(file("site"))
  .enablePlugins(ScalaJSBundlerPlugin)
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
      "me.shadaj" %%% "slinky-core" % "0.6.5",
      "me.shadaj" %%% "slinky-web" % "0.6.5",
      "me.shadaj" %%% "slinky-native" % "0.6.5",
      "me.shadaj" %%% "slinky-hot" % "0.6.5",
      //"me.shadaj" %%% "slinky-scalajsreact-interop" % "0.6.5", // not avail for 2.13 yet
      "org.scalatest" %%% "scalatest" % "3.1.1" % Test
    ),
    npmDependencies in Compile ++= Seq(
      "react" -> "16.13.1",
      "react-dom" -> "16.13.1",
      "react-proxy" -> "1.1.8"
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
    addCommandAlias("build", "fullOptJS::webpack")
  )
  .dependsOn(common.js)

lazy val common = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("common"))
  .settings(sharedSettings)
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
  version := "0.3.0",
  scalaVersion := "2.13.3",
  scalacOptions ++= Seq(
    "-Ymacro-annotations"
  ),
  libraryDependencies ++= Seq(
    "dev.zio"                         %% "zio"                                % ZIOVersion,
    "dev.zio"                         %% "zio-macros"                         % ZIOVersion,
    "dev.zio"                         %% "zio-kafka"                          % "0.12.0",
    //"io.grpc"                         %  "grpc-netty"                         % "1.31.1",
    //"com.thesamet.scalapb"            %% "scalapb-runtime-grpc"               % scalapb.compiler.Version.scalapbVersion,
    "io.circe"                        %% "circe-generic"                      % "0.13.0",
    "dev.zio"                         %% "zio-test"                           % ZIOVersion % Test,
    "dev.zio"                         %% "zio-test-sbt"                       % ZIOVersion % Test,
  ),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full),
  testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  /*PB.targets in Compile := Seq(
    scalapb.gen(grpc = true) -> (sourceManaged in Compile).value,
    scalapb.zio_grpc.ZioCodeGenerator -> (sourceManaged in Compile).value,
  )*/
  //,bloopExportJarClassifiers in Global := Some(Set("sources"))
)