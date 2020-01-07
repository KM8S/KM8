name := "KafkaMate"

version := "0.1.1"

scalaVersion := "2.12.9"

lazy val FinchVersion = "0.31.0"

libraryDependencies ++= Seq(
  "dev.zio"                         %% "zio"                                % "1.0.0-RC16",
  "dev.zio"                         %% "zio-kafka"                          % "0.3.2",
  "dev.zio"                         %% "zio-interop-cats"                   % "2.0.0.0-RC7",
  "dev.zio"                         %% "zio-interop-twitter"                % "19.10.0.0-RC3",
  "dev.zio"                         %% "zio-interop-reactivestreams"        % "1.0.3.5-RC1",
  "com.github.finagle"              %% "finchx-core"                        % FinchVersion,
  "com.github.finagle"              %% "finchx-circe"                       % FinchVersion,
  "com.github.finagle"              %% "finchx-fs2"                         % FinchVersion,
  "co.fs2"                          %% "fs2-reactive-streams"               % "2.1.0",
  "io.circe"                        %% "circe-generic"                      % "0.12.2",
  "com.github.mlangc"               %% "slf4zio"                            % "0.3.0",
  "net.logstash.logback"            %  "logstash-logback-encoder"           % "5.0",
  "ch.qos.logback"                  %  "logback-classic"                    % "1.2.3"
)
