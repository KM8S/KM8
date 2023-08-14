ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("ch.epfl.scala"     % "sbt-missinglink"        % "0.3.2")
addSbtPlugin("ch.epfl.scala"     % "sbt-bloop"              % "1.4.8-63-80fdb462")
addCompilerPlugin("com.olegpy"  %% "better-monadic-for"     % "0.3.1")
addSbtPlugin("io.spray"          % "sbt-revolver"           % "0.9.1")
addSbtPlugin("org.jmotor.sbt"    % "sbt-dependency-updates" % "1.2.2")
addSbtPlugin("com.thesamet"      % "sbt-protoc"             % "1.0.6")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker"             % "1.8.2")
addSbtPlugin("com.eed3si9n"      % "sbt-assembly"           % "1.0.0")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"           % "2.5.0")
addSbtPlugin("ch.epfl.scala"     % "sbt-scalafix"           % "0.10.4")

libraryDependencies ++= Seq(
  "com.thesamet.scalapb"          %% "compilerplugin"           % "0.11.13",
  "com.thesamet.scalapb.grpcweb"  %% "scalapb-grpcweb-code-gen" % "0.6.6",
  "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen"         % "0.5.3"
)

// For Scala.js:
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.13.2")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
addSbtPlugin("ch.epfl.scala"      % "sbt-scalajs-bundler"      % "0.20.0")
