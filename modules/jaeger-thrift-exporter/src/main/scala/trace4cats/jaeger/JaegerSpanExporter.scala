package trace4cats.jaeger

import cats.Foldable
import cats.effect.kernel.syntax.async._
import cats.effect.kernel.{Async, Resource, Sync}
import cats.syntax.foldable._
import cats.syntax.functor._
import io.jaegertracing.thrift.internal.senders.UdpSender
import io.jaegertracing.thriftjava.{Process, Span}
import trace4cats.kernel.SpanExporter
import trace4cats.model.{Batch, TraceProcess}

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.util.Try

object JaegerSpanExporter {
  def apply[F[_]: Async, G[_]: Foldable](
    process: Option[TraceProcess],
    host: String = Option(System.getenv("JAEGER_AGENT_HOST")).getOrElse(UdpSender.DEFAULT_AGENT_UDP_HOST),
    port: Int = Option(System.getenv("JAEGER_AGENT_PORT"))
      .flatMap(p => Try(p.toInt).toOption)
      .getOrElse(UdpSender.DEFAULT_AGENT_UDP_COMPACT_PORT),
    blocker: Option[ExecutionContext] = None
  ): Resource[F, SpanExporter[F, G]] = {

    def blocking[A](thunk: => A): F[A] =
      blocker.fold(Sync[F].blocking(thunk))(Sync[F].delay(thunk).evalOn)

    Resource.make(Sync[F].delay(new UdpSender(host, port, 0)))(sender => Sync[F].delay(sender.close()).void).map {
      sender =>
        new SpanExporter[F, G] {
          override def exportBatch(batch: Batch[G]): F[Unit] = {
            def send(process: Process, spans: java.util.List[Span]): F[Unit] =
              blocking(sender.send(process, spans))

            process match {
              case None =>
                val grouped: List[(Process, java.util.List[Span])] =
                  batch.spans
                    .foldLeft(Map.empty[String, ListBuffer[Span]]) { case (acc, span) =>
                      acc.updated(
                        span.serviceName,
                        acc
                          .getOrElse(span.serviceName, scala.collection.mutable.ListBuffer.empty[Span]) += JaegerSpan
                          .convert(span)
                      )
                    }
                    .view
                    .map { case (service, spans) => (new Process(service), spans.asJava) }
                    .toList
                grouped.traverse_((send _).tupled)

              case Some(tp) =>
                val spans = batch.spans
                  .foldLeft(ListBuffer.empty[Span]) { (buf, span) =>
                    buf += JaegerSpan.convert(span)
                  }
                  .asJava
                send(JaegerSpan.convert(tp), spans)
            }
          }
        }
    }
  }
}
