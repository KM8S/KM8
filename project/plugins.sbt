ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("ch.epfl.scala" % "sbt-missinglink" % "0.2.0")

addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "1.4.0-RC1")

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

// addSbtPlugin("org.jmotor.sbt" % "sbt-dependency-updates" % "1.2.1")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.34")

addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.8.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.15.0")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")

addSbtPlugin("ch.epfl.scala" % "sbt-scala3-migrate" % "0.4.3")

libraryDependencies ++= Seq(
  "com.thesamet.scalapb"           % "compilerplugin_2.13"            % "0.11.3",
  "com.thesamet.scalapb.grpcweb"   % "scalapb-grpcweb-code-gen_2.13"  % "0.6.4",
  "com.thesamet.scalapb.zio-grpc"  % "zio-grpc-codegen_2.13"          % "0.5.0"
)

dependencyOverrides ++= Seq(
  "com.thesamet.scalapb"           % "protoc-bridge_2.13"             % "0.9.2"
)

// For Scala.js:
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.2.0")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")

addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.18.0")
