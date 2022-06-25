ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("ch.epfl.scala"     % "sbt-missinglink"        % "0.3.3")
addSbtPlugin("ch.epfl.scala"     % "sbt-bloop"              % "1.4.8-63-80fdb462")
addSbtPlugin("io.spray"          % "sbt-revolver"           % "0.9.1")
addSbtPlugin("org.jmotor.sbt"    % "sbt-dependency-updates" % "1.2.2")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker"             % "1.9.0")
addSbtPlugin("com.eed3si9n"      % "sbt-assembly"           % "1.2.0")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"           % "2.4.6")
// project/plugins.sbt
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.10.1")