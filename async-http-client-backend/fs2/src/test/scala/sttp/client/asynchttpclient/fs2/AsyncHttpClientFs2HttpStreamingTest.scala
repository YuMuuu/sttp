package sttp.client.asynchttpclient.fs2

import java.nio.ByteBuffer

import cats.effect.{ContextShift, IO}
import cats.instances.string._
import fs2.{Chunk, Stream, text}
import sttp.client.SttpBackend
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.streaming.StreamingTest

import scala.concurrent.{ExecutionContext, Future}

class AsyncHttpClientFs2HttpStreamingTest extends StreamingTest[IO, Stream[IO, ByteBuffer]] {

  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)

  override implicit val backend: SttpBackend[IO, Stream[IO, ByteBuffer]] =
    AsyncHttpClientFs2Backend[IO]().unsafeRunSync()

  override implicit val convertToFuture: ConvertToFuture[IO] =
    new ConvertToFuture[IO] {
      override def toFuture[T](value: IO[T]): Future[T] =
        value.unsafeToFuture()
    }

  override def bodyProducer(body: String): Stream[IO, ByteBuffer] =
    Stream.emits(body.getBytes("utf-8")).map(b => ByteBuffer.wrap(Array(b)))

  override def bodyConsumer(stream: Stream[IO, ByteBuffer]): IO[String] =
    stream
      .map(bb => Chunk.array(bb.array))
      .through(text.utf8DecodeC)
      .compile
      .foldMonoid

}