package sttp.client3.okhttp.fs2

import cats.effect.IO
import org.scalatest.OptionValues
import sttp.client3.impl.cats.CatsTestBase
import sttp.client3.testing.websocket.{WebSocketBufferOverflowTest, WebSocketConcurrentTest, WebSocketTest}
import sttp.capabilities
import sttp.client3.SttpBackend
import sttp.client3.okhttp.OkHttpBackend

import scala.concurrent.duration.FiniteDuration

class OKHttpFs2WebSocketTest
    extends WebSocketTest[IO]
    with WebSocketBufferOverflowTest[IO]
    with WebSocketConcurrentTest[IO]
    with CatsTestBase
    with OptionValues {

  import cats.effect.Blocker

  import scala.concurrent.ExecutionContext.global

  override def bufferCapacity: Int = OkHttpBackend.DefaultWebSocketBufferCapacity.value

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => IO[T]): IO[T] = {
    def tryWithCounter(i: Int): IO[T] = {
      import cats.implicits.{catsSyntaxApplicativeError, catsSyntaxFlatMapOps}
      (IO.sleep(interval) >> f).recoverWith {
        case _: Exception if i < attempts => tryWithCounter(i + 1)
      }
    }

    tryWithCounter(0)
  }

  override val backend: SttpBackend[IO, capabilities.WebSockets] =
    OkHttpFs2Backend[IO](Blocker.liftExecutionContext(global)).unsafeRunSync()

  import cats.syntax.traverse._
  override def concurrently[T](fs: List[() => IO[T]]): IO[List[T]] = fs.traverse(_())

}
