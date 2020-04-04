name := "KafkaMate"

version := "0.1.1"

scalaVersion := "2.12.11"

lazy val ZIOVersion = "1.0.0-RC18-2"
lazy val FinchVersion = "0.32.1"

libraryDependencies ++= Seq(
  "org.typelevel"                   %% "cats-effect"                        % "2.1.2",
  "org.typelevel"                   %% "cats-core"                          % "2.1.1",
  "dev.zio"                         %% "zio"                                % ZIOVersion,
  "dev.zio"                         %% "zio-kafka"                          % "0.7.0",
  "dev.zio"                         %% "zio-interop-cats"                   % "2.0.0.0-RC12",
  "dev.zio"                         %% "zio-interop-twitter"                % "20.3.0.0-RC1",
  "dev.zio"                         %% "zio-interop-reactivestreams"        % "1.0.3.5-RC6",
  "com.github.finagle"              %% "finchx-core"                        % FinchVersion,
  "com.github.finagle"              %% "finchx-circe"                       % FinchVersion,
  "com.github.finagle"              %% "finchx-fs2"                         % FinchVersion,
  "com.fasterxml.jackson.module"    %% "jackson-module-scala"               % "2.10.0",
  "co.fs2"                          %% "fs2-reactive-streams"               % "2.2.2",
  "io.circe"                        %% "circe-generic"                      % "0.13.0",
  "com.github.mlangc"               %% "slf4zio"                            % "0.5.1",
  "net.logstash.logback"            %  "logstash-logback-encoder"           % "6.3",
  "ch.qos.logback"                  %  "logback-classic"                    % "1.2.3",
  "dev.zio"                         %% "zio-test"                           % ZIOVersion % Test,
  "dev.zio"                         %% "zio-test-sbt"                       % ZIOVersion % Test,
  "io.github.embeddedkafka"         %% "embedded-kafka"                     % "2.4.1" % Test,
  compilerPlugin("org.typelevel"    %  "kind-projector"                     % "0.11.0" cross CrossVersion.full)
)

testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))

//bloopExportJarClassifiers in Global := Some(Set("sources"))