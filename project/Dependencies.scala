import sbt._

object Dependencies {
  object Versions {
    val scala212 = "2.12.15"
    val scala213 = "2.13.8"
    val scala3 = "3.1.2"

    val trace4cats = "0.13.1+67-a7d9b849"
    val trace4catsExporterHttp = "0.13.1+14-639b5d65"
    val trace4catsJaegerIntegrationTest = "0.13.1+17-8394f0da"

    val collectionCompat = "2.7.0"
    val jaeger = "1.8.1"

    val kindProjector = "0.13.2"
    val betterMonadicFor = "0.3.1"
  }

  lazy val trace4catsCore = "io.janstenpickle"         %% "trace4cats-core"          % Versions.trace4cats
  lazy val trace4catsExporterHttp = "io.janstenpickle" %% "trace4cats-exporter-http" % Versions.trace4catsExporterHttp
  lazy val trace4catsJaegerIntegrationTest =
    "io.janstenpickle" %% "trace4cats-jaeger-integration-test" % Versions.trace4catsJaegerIntegrationTest

  lazy val collectionCompat = "org.scala-lang.modules" %% "scala-collection-compat" % Versions.collectionCompat
  lazy val jaegerThrift = "io.jaegertracing"            % "jaeger-thrift"           % Versions.jaeger

  lazy val kindProjector = ("org.typelevel" % "kind-projector"     % Versions.kindProjector).cross(CrossVersion.full)
  lazy val betterMonadicFor = "com.olegpy" %% "better-monadic-for" % Versions.betterMonadicFor
}
