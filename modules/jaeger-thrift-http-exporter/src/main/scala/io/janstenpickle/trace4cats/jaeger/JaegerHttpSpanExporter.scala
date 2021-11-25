package io.janstenpickle.trace4cats.jaeger

import cats.Foldable
import cats.effect.kernel.Async
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import fs2.{Chunk, Stream}
import io.jaegertracing.thriftjava.{Batch => JaegerBatch, Process, Span}
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.{Batch, TraceProcess}
import org.apache.thrift.TSerializer
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`

import scala.util.control.NonFatal

object JaegerHttpSpanExporter {

  private val thriftBinary: List[Header.ToRaw] = List(`Content-Type`(MediaType.application.`vnd.apache.thrift.binary`))

  private implicit def jaegerBatchEncoder[F[_]: Async](implicit
    serializer: TSerializer
  ): EntityEncoder[F, JaegerBatch] =
    new EntityEncoder[F, JaegerBatch] {
      def toEntity(a: JaegerBatch): Entity[F] = try {
        val payload = serializer.serialize(a)
        Entity[F](Stream.chunk[F, Byte](Chunk.array(payload)), Some(payload.length.toLong))
      } catch {
        case NonFatal(e) => Entity[F](Stream.eval(Async[F].raiseError(e)), None)
      }

      val headers: Headers = Headers(thriftBinary)
    }

  private def makeBatch[G[_]: Foldable](jaegerProcess: Process, batch: Batch[G]): JaegerBatch = {
    val spans = batch.spans.foldLeft(new java.util.ArrayList[Span]) { case (acc, span) =>
      acc.add(JaegerSpan.convert(span))
      acc
    }
    new JaegerBatch(jaegerProcess, spans)
  }

  def apply[F[_]: Async, G[_]: Foldable](
    client: Client[F],
    process: TraceProcess,
    host: String = "localhost",
    port: Int = 14268
  ): F[SpanExporter[F, G]] = Uri.fromString(s"http://$host:$port/api/traces").liftTo[F].flatMap { uri =>
    apply(client, process, uri)
  }

  def apply[F[_]: Async, G[_]: Foldable](client: Client[F], process: TraceProcess, uri: Uri): F[SpanExporter[F, G]] =
    Async[F].catchNonFatal(new TSerializer()).map { implicit serializer: TSerializer =>
      val jprocess = JaegerSpan.convert(process)
      HttpSpanExporter[F, G, JaegerBatch](client, uri, (batch: Batch[G]) => makeBatch[G](jprocess, batch), thriftBinary)
    }
}
