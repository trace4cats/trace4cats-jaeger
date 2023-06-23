package trace4cats.jaeger

import java.time.Instant
import cats.effect.IO
import fs2.Chunk
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.middleware.Logger
import trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import trace4cats.test.jaeger.BaseJaegerSpec
import trace4cats.{CompleterConfig, SemanticTags}

import scala.concurrent.duration._

class JaegerHttpSpanCompleterSpec extends BaseJaegerSpec {
  it should "Send a span to jaeger" in forAll { (span: CompletedSpan.Builder, process: TraceProcess) =>
    val updatedSpan =
      span.copy(start = Instant.now(), end = Instant.now(), attributes = span.attributes -- excludedTagKeys)
    val batch = Batch(Chunk(updatedSpan.build(process)))
    val completer =
      BlazeClientBuilder[IO].resource
        .map(Logger.apply(logHeaders = true, logBody = true, logAction = Some(IO.println(_))))
        .flatMap { client =>
          JaegerHttpSpanCompleter[IO](
            client,
            process,
            "localhost",
            14268,
            config = CompleterConfig(batchTimeout = 50.millis)
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
