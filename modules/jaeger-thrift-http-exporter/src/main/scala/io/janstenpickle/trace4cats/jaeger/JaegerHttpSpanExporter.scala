package io.janstenpickle.trace4cats.jaeger

import cats.Foldable
import cats.effect.kernel.Async
import cats.syntax.either._
import cats.syntax.foldable._
import cats.syntax.functor._
import io.jaegertracing.thriftjava.{Batch => JaegerBatch, Process, Span}
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.{Batch, TraceProcess}
import org.apache.thrift.TSerializer
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`
import org.http4s.{Header, MediaType, Uri}

object JaegerHttpSpanExporter {

  private val headers: List[Header.ToRaw] = List(`Content-Type`(MediaType.application.`vnd.apache.thrift.binary`))

  private val serializer = new TSerializer()

  private def makePayload[G[_]: Foldable](jaegerProcess: Process, batch: Batch[G]): Array[Byte] = {
    val spans = batch.spans.foldLeft(new java.util.ArrayList[Span]) { case (acc, span) =>
      acc.add(JaegerSpan.convert(span))
      acc
    }
    val jaegerBatch = new JaegerBatch(jaegerProcess, spans)
    serializer.serialize(jaegerBatch)
  }

  def apply[F[_]: Async, G[_]: Foldable](
    client: Client[F],
    process: TraceProcess,
    host: String = "localhost",
    port: Int = 14268
  ): F[SpanExporter[F, G]] = Uri.fromString(s"http://$host:$port/api/traces").liftTo[F].map { uri =>
    HttpSpanExporter[F, G, Array[Byte]](client, uri, makePayload[G](JaegerSpan.convert(process), _), headers)
  }

  def apply[F[_]: Async, G[_]: Foldable](client: Client[F], process: TraceProcess, uri: Uri): SpanExporter[F, G] =
    HttpSpanExporter[F, G, Array[Byte]](client, uri, makePayload[G](JaegerSpan.convert(process), _), headers)
}
