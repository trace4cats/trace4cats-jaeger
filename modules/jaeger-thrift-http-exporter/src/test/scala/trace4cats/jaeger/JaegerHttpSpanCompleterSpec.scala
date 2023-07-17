package trace4cats.jaeger

import cats.effect.IO
import cats.effect.kernel.Resource
import fs2.Chunk
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.middleware.Logger
import trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import trace4cats.test.jaeger.BaseJaegerSpec
import trace4cats.{SemanticTags, SpanCompleter}

import java.time.Instant

class JaegerHttpSpanCompleterSpec extends BaseJaegerSpec {
  it should "Send a span to jaeger" in forAll { (span: CompletedSpan.Builder, process: TraceProcess) =>
    val updatedSpan =
      span.copy(start = Instant.now(), end = Instant.now(), attributes = span.attributes -- excludedTagKeys)
    val batch = Batch(Chunk(updatedSpan.build(process)))
    val completer =
      BlazeClientBuilder[IO].resource
        .map(Logger.apply(logHeaders = true, logBody = false, logAction = Some(IO.println(_))))
        .flatMap { client =>
          Resource
            .eval(JaegerHttpSpanExporter[IO, Chunk](client, process, "localhost", 14268, "http"))
            .map(exp =>
              new SpanCompleter[IO] {
                override def complete(span: CompletedSpan.Builder): IO[Unit] =
                  exp.exportBatch(Batch(Chunk(span.build(process))))
              }
            )
        }

    testCompleter(
      completer,
      updatedSpan,
      process,
      batchToJaegerResponse(
        batch,
        process,
        SemanticTags.kindTags,
        SemanticTags.statusTags("span."),
        SemanticTags.processTags,
        internalSpanFormat = "jaeger"
      )
    )
  }
}
