import sbt._

object Dependencies {
  object Versions {
    val scala212 = "2.12.14"
    val scala213 = "2.13.6"

    val trace4cats = "0.12.0-RC1+146-d193db1e"

    val jaeger = "1.6.0"
  }

  lazy val trace4catsExporterCommon = "io.janstenpickle" %% "trace4cats-exporter-common" % Versions.trace4cats
  lazy val trace4catsKernel = "io.janstenpickle"         %% "trace4cats-kernel"          % Versions.trace4cats
  lazy val trace4catsJaegerIntegrationTest =
    "io.janstenpickle"                          %% "trace4cats-jaeger-integration-test" % Versions.trace4cats
  lazy val trace4catsModel = "io.janstenpickle" %% "trace4cats-model"                   % Versions.trace4cats

  lazy val jaegerThrift = "io.jaegertracing" % "jaeger-thrift" % Versions.jaeger
}
