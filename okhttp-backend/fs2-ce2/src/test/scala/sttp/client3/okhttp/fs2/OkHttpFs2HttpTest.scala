package sttp.client3.okhttp.fs2

import cats.effect.IO
import sttp.client3.impl.cats.CatsTestBase
import sttp.client3.testing.HttpTest
import cats.effect.Blocker
import sttp.client3.SttpBackend

import scala.concurrent.ExecutionContext.global

class OkHttpFs2HttpTest extends HttpTest[IO] with CatsTestBase {

  override val backend: SttpBackend[IO, Any] =
    OkHttpFs2Backend[IO](Blocker.liftExecutionContext(global)).unsafeRunSync()

  override def throwsExceptionOnUnsupportedEncoding = false

  override def supportsAutoDecompressionDisabling = false
}
