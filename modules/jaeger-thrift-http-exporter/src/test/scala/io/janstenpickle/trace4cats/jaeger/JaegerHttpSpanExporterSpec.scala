package io.janstenpickle.trace4cats.jaeger

import cats.effect.{IO, Resource}
import fs2.Chunk
import io.janstenpickle.trace4cats.`export`.SemanticTags
import io.janstenpickle.trace4cats.model.{Batch, TraceProcess}
import io.janstenpickle.trace4cats.test.jaeger.BaseJaegerSpec
import org.http4s.blaze.client.BlazeClientBuilder

import java.time.Instant

class JaegerHttpSpanExporterSpec extends BaseJaegerSpec {
  it should "Send a batch of spans to jaeger" in forAll { (batch: Batch[Chunk], process: TraceProcess) =>
    val updatedBatch =
      Batch(
        batch.spans.map(span =>
          span.copy(
            serviceName = process.serviceName,
            attributes = process.attributes ++ span.attributes -- excludedTagKeys,
            start = Instant.now(),
            end = Instant.now()
          )
        )
      )
    val exporter = BlazeClientBuilder[IO].resource.flatMap { client =>
      Resource.eval(JaegerHttpSpanExporter[IO, Chunk](client, process, "localhost", 14268))
    }

    testExporter(
      exporter,
      updatedBatch,
      batchToJaegerResponse(
        updatedBatch,
        process,
        SemanticTags.kindTags,
        SemanticTags.statusTags("span."),
        SemanticTags.processTags,
        internalSpanFormat = "jaeger"
      )
    )
  }
}
