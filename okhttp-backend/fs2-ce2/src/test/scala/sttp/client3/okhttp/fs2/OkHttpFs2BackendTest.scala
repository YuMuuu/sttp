package sttp.client3.okhttp.fs2

import cats.effect.{Blocker, ContextShift, IO}
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.SttpBackend
import sttp.client3.impl.fs2.Fs2StreamingTest

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

class OkHttpFs2BackendTest extends Fs2StreamingTest {

  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  override def backend: SttpBackend[IO, Fs2Streams[IO]] =
    OkHttpFs2Backend[IO](Blocker.liftExecutionContext(global)).unsafeRunSync()

  override protected def supportsStreamingMultipartParts: Boolean = false
}
