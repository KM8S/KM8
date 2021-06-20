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

libraryDependencies ++= Seq(
  "com.thesamet.scalapb"          %% "compilerplugin"           % "0.10.8",
  "com.thesamet.scalapb.grpcweb"  %% "scalapb-grpcweb-code-gen" % "0.4.1+21-1dbb6ad7-SNAPSHOT",
  "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen"         % "0.4.0"
)

// For Scala.js:
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.2.0")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")

addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.18.0")
