package trace4cats.jaeger

import cats.Applicative
import cats.effect.kernel.syntax.monadCancel._
import cats.effect.kernel.syntax.spawn._
import cats.effect.kernel.{Resource, Temporal}
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.concurrent.Channel
import fs2.{Chunk, Stream}
import org.typelevel.log4cats.Logger
import trace4cats._

object QueuedSpanCompleter {
  def apply[F[_]: Temporal: Logger](
    process: TraceProcess,
    exporter: SpanExporter[F, Chunk],
    config: CompleterConfig
  ): Resource[F, SpanCompleter[F]] = {
    val realBufferSize = if (config.bufferSize < config.batchSize * 5) config.batchSize * 5 else config.bufferSize

    def exportBatches(stream: Stream[F, CompletedSpan]): F[Unit] =
      stream
        .evalTap(span => Logger[F].info(s"Stream saw: ${span.context.traceId}"))
        .groupWithin(config.batchSize, config.batchTimeout)
        .evalTap(chunk => Logger[F].info(s"Grouped a chunk: ${chunk.map(_.context.traceId).toList}"))
        .evalMap { spans =>
          Stream
            .retry(
              exporter.exportBatch(Batch(spans)).guaranteeCase(ec => Logger[F].info(s"Exported: $ec")),
              delay = config.retryConfig.delay,
              nextDelay = config.retryConfig.nextDelay.calc,
              maxAttempts = config.retryConfig.maxAttempts
            )
            .compile
            .drain
            .onError { case th =>
              Logger[F].warn(th)("Failed to export spans")
            }
            .guaranteeCase(ec => Logger[F].info(s"Retry stream closing with $ec"))
            .uncancelable
        }
        .compile
        .drain
        .guaranteeCase(ec => Logger[F].info(s"Span stream closing with $ec"))

    for {
      channel <- Resource.eval(Channel.bounded[F, CompletedSpan](realBufferSize))
      errorChannel <- Resource.eval(Channel.bounded[F, Either[Channel.Closed, Boolean]](1))
      _ <- errorChannel.stream
        .evalScan(false) {
          case (false, Right(false)) =>
            Logger[F].warn(s"Failed to enqueue new span, buffer is full of $realBufferSize").as(true)
          case (false, Left(_)) =>
            Logger[F].warn(s"Failed to enqueue new span, channel is closed").as(true)
          case (true, _) => Applicative[F].pure(true)
          case (_, Right(true)) => Applicative[F].pure(false)
        }
        .compile
        .drain
        .uncancelable
        .guaranteeCase(ec => Logger[F].info(s"Error stream closing with $ec"))
        .background
      fib <- exportBatches(channel.stream)
        .flatTap(_ => errorChannel.close)
        .uncancelable
        .background
        .onFinalize(Logger[F].info("Shut down queued span completer"))
      _ <- Resource.onFinalize(fib.void)
      _ <- Resource.onFinalize(channel.close.void)
      _ <- Resource.onFinalize(Logger[F].info("Shutting down queued span completer..."))
    } yield new SpanCompleter[F] {
      override def complete(span: CompletedSpan.Builder): F[Unit] =
        channel
          .trySend(span.build(process))
          .flatTap(ts => Logger[F].info(s"channel.trySend for ${span.context.traceId}: $ts"))
          .flatMap(errorChannel.send)
          .flatTap(ts => Logger[F].info(s"errorChannel.send for ${span.context.traceId}: $ts"))
          .void
    }
  }
}
