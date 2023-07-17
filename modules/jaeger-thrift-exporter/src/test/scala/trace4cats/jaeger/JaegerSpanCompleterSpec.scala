package trace4cats.jaeger

import java.time.Instant
import cats.effect.IO
import fs2.Chunk
import trace4cats.model.{CompletedSpan, TraceProcess}
import trace4cats.test.jaeger.BaseJaegerSpec
import trace4cats.{Batch, CompleterConfig, SemanticTags, SpanCompleter}

import scala.concurrent.duration._

class JaegerSpanCompleterSpec extends BaseJaegerSpec {
  it should "Send a span to jaeger" in forAll { (span: CompletedSpan.Builder, process: TraceProcess) =>
    val updatedSpan =
      span.copy(start = Instant.now(), end = Instant.now(), attributes = span.attributes -- excludedTagKeys)
    val batch = Batch(Chunk(updatedSpan.build(process)))

    testCompleter(
      JaegerSpanCompleter[IO](process, "localhost", 6831, config = CompleterConfig(batchTimeout = 50.millis)).map {
        inner => (span: CompletedSpan.Builder) => inner.complete(span) >> IO.sleep(1.second)
      },
      updatedSpan,
      process,
      batchToJaegerResponse(
        batch,
        process,
        SemanticTags.kindTags,
        SemanticTags.statusTags("span."),
        SemanticTags.processTags
      )
    )
  }
}
