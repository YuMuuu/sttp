package sttp.client3.httpclient

import java.util.concurrent.Semaphore
import scala.concurrent.{ExecutionContext, Future, blocking}

private[httpclient] class FutureSequencer(implicit ec: ExecutionContext) extends Sequencer[Future] {
  private val semaphore = new Semaphore(1)

  def apply[T](t: => Future[T]): Future[T] = {
    blocking {
      semaphore.acquire()
    }
    t.andThen { case _ => semaphore.release() }
  }
}
