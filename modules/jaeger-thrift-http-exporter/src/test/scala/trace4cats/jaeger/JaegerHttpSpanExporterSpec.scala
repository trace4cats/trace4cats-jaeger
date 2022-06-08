package trace4cats.jaeger

import java.time.Instant

import cats.effect.{IO, Resource}
import fs2.Chunk
import org.http4s.blaze.client.BlazeClientBuilder
import trace4cats.SemanticTags
import trace4cats.model.{Batch, TraceProcess}
import trace4cats.test.jaeger.BaseJaegerSpec

class JaegerHttpSpanExporterSpec extends BaseJaegerSpec {
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
