package trace4cats.jaeger

import java.time.Instant

import cats.effect.IO
import fs2.Chunk
import trace4cats.SemanticTags
import trace4cats.model.{Batch, TraceProcess}
import trace4cats.test.jaeger.BaseJaegerSpec

class JaegerSpanExporterSpec extends BaseJaegerSpec {
  it should "Send a batch of spans to jaeger" in forAll { (batch: Batch[Chunk], process: TraceProcess) =>
    val updatedBatch =
      Batch(
        batch.spans.map(span =>
          span.copy(
            serviceName = process.serviceName,
            attributes = (process.attributes ++ span.attributes) -- excludedTagKeys,
            start = Instant.now(),
            end = Instant.now()
          )
        )
      )

    testExporter(
      JaegerSpanExporter[IO, Chunk](Some(process), "localhost", 6831),
      updatedBatch,
      batchToJaegerResponse(
        updatedBatch,
        process,
        SemanticTags.kindTags,
        SemanticTags.statusTags("span."),
        SemanticTags.processTags
      )
    )
  }
}
