name := "KafkaMate"

version := "0.1.1"

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
lazy val FinchVersion = "0.32.1"

libraryDependencies ++= Seq(
  "org.typelevel"                   %% "cats-effect"                        % "2.1.4",
  "org.typelevel"                   %% "cats-core"                          % "2.1.1",
  "dev.zio"                         %% "zio"                                % ZIOVersion,
  "dev.zio"                         %% "zio-macros"                         % ZIOVersion,
  "dev.zio"                         %% "zio-kafka"                          % "0.12.0",
  "dev.zio"                         %% "zio-interop-cats"                   % "2.1.4.0",
  "dev.zio"                         %% "zio-interop-twitter"                % "20.7.0.0",
  "dev.zio"                         %% "zio-interop-reactivestreams"        % "1.0.3.5",
  "com.github.finagle"              %% "finchx-core"                        % FinchVersion,
  "com.github.finagle"              %% "finchx-circe"                       % FinchVersion,
  "com.github.finagle"              %% "finchx-fs2"                         % FinchVersion,
  "com.fasterxml.jackson.module"    %% "jackson-module-scala"               % "2.10.0",
  "co.fs2"                          %% "fs2-reactive-streams"               % "2.4.2",
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

//bloopExportJarClassifiers in Global := Some(Set("sources"))