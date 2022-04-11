lazy val commonSettings = Seq(
  Compile / compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(compilerPlugin(Dependencies.kindProjector), compilerPlugin(Dependencies.betterMonadicFor))
      case _ => Seq.empty
    }
  },
  scalacOptions += {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => "-Wconf:any:wv"
      case _ => "-Wconf:any:v"
    }
  },
  Test / fork := true,
  resolvers += Resolver.sonatypeRepo("releases"),
)

lazy val noPublishSettings =
  commonSettings ++ Seq(publish := {}, publishArtifact := false, publishTo := None, publish / skip := true)

lazy val publishSettings = commonSettings ++ Seq(
  publishMavenStyle := true,
  pomIncludeRepository := { _ =>
    false
  },
  Test / publishArtifact := false
)

lazy val root = (project in file("."))
  .settings(noPublishSettings)
  .settings(name := "Trace4Cats Jaeger")
  .aggregate(`jaeger-thrift-common`, `jaeger-thrift-exporter`, `jaeger-thrift-http-exporter`)

lazy val `jaeger-thrift-common` =
  (project in file("modules/jaeger-thrift-common"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-jaeger-thrift-common",
      libraryDependencies ++= Seq(
        Dependencies.collectionCompat,
        Dependencies.jaegerThrift,
        Dependencies.trace4catsKernel,
        Dependencies.trace4catsModel,
        Dependencies.trace4catsExporterCommon
      )
    )

lazy val `jaeger-thrift-exporter` =
  (project in file("modules/jaeger-thrift-exporter"))
    .dependsOn(`jaeger-thrift-common`)
    .settings(publishSettings)
    .settings(
      name := "trace4cats-jaeger-thrift-exporter",
      libraryDependencies ++= Seq(Dependencies.trace4catsJaegerIntegrationTest).map(_ % Test)
    )

lazy val `jaeger-thrift-http-exporter` =
  (project in file("modules/jaeger-thrift-http-exporter"))
    .dependsOn(`jaeger-thrift-common`)
    .settings(publishSettings)
    .settings(
      name := "trace4cats-jaeger-thrift-http-exporter",
      libraryDependencies ++= Seq(Dependencies.trace4catsExporterHttp),
      libraryDependencies ++= Seq(Dependencies.trace4catsJaegerIntegrationTest).map(_ % Test)
    )
