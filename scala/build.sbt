ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.2.2"

lazy val root = (project in file("."))
  .settings(
    name := "generator",
    libraryDependencies ++= Seq(
      "org.scala-lang" %% "scala3-compiler" % scalaVersion.value,
      "org.apache.commons" % "commons-configuration2" % "2.8.0",
      "com.hubspot.jinjava" % "jinjava" % "2.7.0",
      "info.picocli" % "picocli" % "4.7.2",
    )
  )

//fork := true

// https://docs.scala-lang.org/scala3/guides/migration/tooling-syntax-rewriting.html
// build.sbt, for Scala 3 project
//scalacOptions ++= Seq("-new-syntax", "-rewrite")
//scalacOptions ++= Seq("-indent", "-rewrite")
scalacOptions ++= Seq("--explain")

addCommandAlias("r", ";run")
addCommandAlias("t", ";test")
addCommandAlias("rl", ";reload")
addCommandAlias("c", ";compile")
addCommandAlias("p", ";universal:packageZipTarball")
addCommandAlias("d", ";docker:publishLocal")
addCommandAlias("fmt", ";scalafmtAll")
addCommandAlias("f", ";scalafmtAll;scalafixAll")
addCommandAlias("fck", ";scalafixAll --check")
