ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("ch.epfl.scala" % "sbt-missinglink" % "0.2.0")

addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "1.4.0-RC1")

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.34")

libraryDependencies ++= Seq("com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % "0.4.0-RC3+10-8091a078-SNAPSHOT")

// For Scala.js:
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.1.1")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")

addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.18.0")