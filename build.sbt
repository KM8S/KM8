name := "KafkaMate"

version := "0.1.1"

scalaVersion := "2.12.9"

lazy val ZIOVersion = "1.0.0-RC16"
lazy val FinchVersion = "0.31.0"

libraryDependencies ++= Seq(
  "dev.zio"                         %% "zio"                                % ZIOVersion,
  "dev.zio"                         %% "zio-kafka"                          % "0.3.2",
  "dev.zio"                         %% "zio-interop-cats"                   % "2.0.0.0-RC6",
  "io.circe"                        %% "circe-generic"                      % "0.12.2",
  "com.github.finagle"              %% "finchx-core"                        % FinchVersion,
  "com.github.finagle"              %% "finchx-circe"                       % FinchVersion,
  "net.logstash.logback"            %  "logstash-logback-encoder"           % "5.0",
  "ch.qos.logback"                  %  "logback-classic"                    % "1.2.3"
)