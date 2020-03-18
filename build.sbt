name := "KafkaMate"

version := "0.1.1"

scalaVersion := "2.12.10"

lazy val ZIOVersion = "1.0.0-RC17"//+396-b16c7e10-SNAPSHOT"
lazy val FinchVersion = "0.32.1"

libraryDependencies ++= Seq(
  "org.typelevel"                   %% "cats-effect"                        % "2.0.0",
  "dev.zio"                         %% "zio"                                % ZIOVersion,
  "dev.zio"                         %% "zio-kafka"                          % "0.5.0",
  "dev.zio"                         %% "zio-interop-cats"                   % "2.0.0.0-RC10",
  "dev.zio"                         %% "zio-interop-twitter"                % "19.12.0.0-RC1",
  "dev.zio"                         %% "zio-interop-reactivestreams"        % "1.0.3.5-RC2",
  "com.github.finagle"              %% "finchx-core"                        % FinchVersion,
  "com.github.finagle"              %% "finchx-circe"                       % FinchVersion,
  "com.github.finagle"              %% "finchx-fs2"                         % FinchVersion,
  "co.fs2"                          %% "fs2-reactive-streams"               % "2.1.0",
  "io.circe"                        %% "circe-generic"                      % "0.12.2",
  "com.github.mlangc"               %% "slf4zio"                            % "0.4.0",
  "net.logstash.logback"            %  "logstash-logback-encoder"           % "5.0",
  "ch.qos.logback"                  %  "logback-classic"                    % "1.2.3",
  "dev.zio"                         %% "zio-test"                           % ZIOVersion % Test,
  "dev.zio"                         %% "zio-test-sbt"                       % ZIOVersion % Test,
  "io.github.embeddedkafka"         %% "embedded-kafka"                     % "2.4.0" % Test,
  compilerPlugin("org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full)
)

//resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))

//bloopExportJarClassifiers in Global := Some(Set("sources"))