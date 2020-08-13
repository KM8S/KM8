name := "KafkaMate"

version := "0.2.0"

scalaVersion := "2.12.11"

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-encoding", "utf8",
  "-target:jvm-1.8",
  "-feature",
  "-language:_",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-macros:after",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xlint",
  //"-Xfatal-warnings",
  "-Xlog-reflective-calls",
  "-Xfuture"
)

resolvers += Resolver.sonatypeRepo("public")

lazy val ZIOVersion = "1.0.0"

libraryDependencies ++= Seq(
  "dev.zio"                         %% "zio"                                % ZIOVersion,
  "dev.zio"                         %% "zio-macros"                         % ZIOVersion,
  "dev.zio"                         %% "zio-kafka"                          % "0.12.0",
  "io.grpc"                         %  "grpc-netty"                         % "1.31.0",
  "com.thesamet.scalapb"            %% "scalapb-runtime-grpc"               % scalapb.compiler.Version.scalapbVersion,
  "com.fasterxml.jackson.module"    %% "jackson-module-scala"               % "2.10.0",
  "io.circe"                        %% "circe-generic"                      % "0.13.0",
  "com.github.mlangc"               %% "slf4zio"                            % "0.7.0",
  "net.logstash.logback"            %  "logstash-logback-encoder"           % "6.3",
  "ch.qos.logback"                  %  "logback-classic"                    % "1.2.3",
  "dev.zio"                         %% "zio-test"                           % ZIOVersion % Test,
  "dev.zio"                         %% "zio-test-sbt"                       % ZIOVersion % Test,
  "io.github.embeddedkafka"         %% "embedded-kafka"                     % "2.4.1" % Test,
  compilerPlugin("org.typelevel"   % "kind-projector" % "0.11.0" cross CrossVersion.full),
  compilerPlugin("org.scalamacros" % "paradise"       % "2.1.1"  cross CrossVersion.full)
)

testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))

PB.targets in Compile := Seq(
  scalapb.gen(grpc = true) -> (sourceManaged in Compile).value / "scalapb",
  scalapb.zio_grpc.ZioCodeGenerator -> (sourceManaged in Compile).value / "scalapb"
)

//bloopExportJarClassifiers in Global := Some(Set("sources"))