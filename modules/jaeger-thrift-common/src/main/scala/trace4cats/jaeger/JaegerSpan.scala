package trace4cats.jaeger

import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

import cats.data.NonEmptyList
import cats.syntax.show._
import io.jaegertracing.thriftjava._
import trace4cats.SemanticTags
import trace4cats.model.AttributeValue._
import trace4cats.model._

import scala.jdk.CollectionConverters._

private[jaeger] object JaegerSpan {

  private val statusTags = SemanticTags.statusTags("span.")

  def makeTags(attributes: Map[String, AttributeValue]): java.util.List[Tag] =
    attributes.view
      .map {
        case (key, StringValue(value)) =>
          new Tag(key, TagType.STRING).setVStr(value.value)
        case (key, DoubleValue(value)) =>
          new Tag(key, TagType.DOUBLE).setVDouble(value.value)
        case (key, BooleanValue(value)) =>
          new Tag(key, TagType.BOOL).setVBool(value.value)
        case (key, LongValue(value)) =>
          new Tag(key, TagType.LONG).setVLong(value.value)
        case (key, value: AttributeList) =>
          new Tag(key, TagType.STRING).setVStr(value.show)
      }
      .toList
      .asJava

  def traceIdToLongs(traceId: TraceId): (Long, Long) = {
    val traceIdBuffer = ByteBuffer.wrap(traceId.value)
    (traceIdBuffer.getLong, traceIdBuffer.getLong)
  }

  def spanIdToLong(spanId: SpanId): Long = ByteBuffer.wrap(spanId.value).getLong

  def references(links: Option[NonEmptyList[Link]]): java.util.List[SpanRef] =
    links
      .fold(List.empty[SpanRef])(_.map { link =>
        val (traceIdHigh, traceIdLow) = traceIdToLongs(link.traceId)
        val spanId = spanIdToLong(link.spanId)
        new SpanRef(SpanRefType.FOLLOWS_FROM, traceIdLow, traceIdHigh, spanId)
      }.toList)
      .asJava

  def convert(process: TraceProcess): Process =
    new Process(process.serviceName).setTags(makeTags(process.attributes))

  def convert(span: CompletedSpan): Span = {
    val (traceIdHigh, traceIdLow) = traceIdToLongs(span.context.traceId)

    val startMicros = TimeUnit.MILLISECONDS.toMicros(span.start.toEpochMilli)
    val endMicros = TimeUnit.MILLISECONDS.toMicros(span.end.toEpochMilli)

    val thriftSpan = new Span(
      traceIdLow,
      traceIdHigh,
      spanIdToLong(span.context.spanId),
      span.context.parent.map(parent => ByteBuffer.wrap(parent.spanId.value).getLong).getOrElse(0),
      span.name,
      span.context.traceFlags.sampled match {
        case SampleDecision.Include => 0
        case SampleDecision.Drop => 1
      },
      startMicros,
      endMicros - startMicros
    )

    thriftSpan
      .setTags(makeTags(span.allAttributes ++ statusTags(span.status) ++ SemanticTags.kindTags(span.kind)))
      .setReferences(references(span.links))
  }

}
