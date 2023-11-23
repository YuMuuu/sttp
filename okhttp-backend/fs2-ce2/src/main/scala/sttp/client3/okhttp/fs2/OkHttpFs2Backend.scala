package sttp.client3.okhttp.fs2

import cats.effect.{Blocker, Concurrent, ConcurrentEffect, ContextShift, Resource, Sync}
import cats.syntax.functor._
import fs2.Stream
import fs2.concurrent.InspectableQueue
import okhttp3.{MediaType, OkHttpClient, RequestBody => OkHttpRequestBody}
import okio.BufferedSink
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.client3.impl.fs2.{Fs2SimpleQueue, Fs2WebSockets}
import sttp.client3.internal.ws.SimpleQueue
import sttp.client3.okhttp.OkHttpBackend.EncodingHandler
import sttp.client3.okhttp.{BodyFromOkHttp, BodyToOkHttp, OkHttpAsyncBackend, OkHttpBackend}
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{DefaultReadTimeout, FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import sttp.ws.{WebSocket, WebSocketFrame}

import java.io.InputStream

class OkHttpFs2Backend[F[_]: ConcurrentEffect: ContextShift] private (
    client: OkHttpClient,
    blocker: Blocker,
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler,
    webSocketBufferCapacity: Option[Int],
    bufferSize: Int = 1024
) extends OkHttpAsyncBackend[F, Fs2Streams[F], Fs2Streams[F] with WebSockets](
      client,
      new CatsMonadAsyncError[F],
      closeClient,
      customEncodingHandler
    ) {

  override val streams = Fs2Streams[F]
  override protected val bodyToOkHttp: BodyToOkHttp[F, Fs2Streams[F]] = new BodyToOkHttp[F, Fs2Streams[F]] {
    override val streams = Fs2Streams[F]

    override def streamToRequestBody(
        stream: streams.BinaryStream,
        mt: MediaType,
        cl: Option[Long]
    ): OkHttpRequestBody = {
      new OkHttpRequestBody() {
        override def contentType(): MediaType = mt
        override def writeTo(sink: BufferedSink): Unit =
          stream.compile.foldChunks(())((_, f) => sink.write(f.toArray))
        override def contentLength(): Long = cl.getOrElse(super.contentLength())
      }
    }

  }
  override protected val bodyFromOkHttp: BodyFromOkHttp[F, Fs2Streams[F]] = new BodyFromOkHttp[F, Fs2Streams[F]] {
    override val streams = Fs2Streams[F]
    override implicit val monad = OkHttpFs2Backend.this.responseMonad

    override def responseBodyToStream(inputStream: InputStream): Stream[F, Byte] =
      fs2.io.readInputStream(Sync[F].pure(inputStream), bufferSize, blocker, closeClient)

    override def compileWebSocketPipe(
        ws: WebSocket[F],
        pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
    ): F[Unit] = Fs2WebSockets.handleThroughPipe(ws)(pipe)
  }

  override protected def createSimpleQueue[T]: F[SimpleQueue[F, T]] = {
    webSocketBufferCapacity
      .fold(InspectableQueue.unbounded[F, T])(InspectableQueue.bounded)
      .map(new Fs2SimpleQueue(_, webSocketBufferCapacity))
  }
}

object OkHttpFs2Backend {
  private val BUFFER_SIZE = 1024
  private def apply[F[_]: ConcurrentEffect: ContextShift](
      client: OkHttpClient,
      blocker: Blocker,
      closeClient: Boolean,
      customEncodingHandler: EncodingHandler,
      webSocketBufferCapacity: Option[Int],
      bufferSize: Int
  ): SttpBackend[F, Fs2Streams[F] with WebSockets] = {
    new FollowRedirectsBackend(
      new OkHttpFs2Backend(
        client,
        blocker,
        closeClient,
        customEncodingHandler,
        webSocketBufferCapacity,
        bufferSize
      )
    )
  }

  def apply[F[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity,
      bufferSize: Int = BUFFER_SIZE
  ): F[SttpBackend[F, Fs2Streams[F] with WebSockets]] = {
    Sync[F].delay(
      OkHttpFs2Backend(
        OkHttpBackend.defaultClient(DefaultReadTimeout.toMillis, options),
        blocker,
        closeClient = true,
        customEncodingHandler,
        webSocketBufferCapacity,
        bufferSize
      )
    )
  }

  def resource[F[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker,
      options: SttpBackendOptions,
      customEncodingHandler: EncodingHandler,
      webSocketBufferCapacity: Option[Int],
      bufferSize: Int
  ): Resource[F, SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    Resource.make(apply(blocker, options, customEncodingHandler, webSocketBufferCapacity, bufferSize))(_.close())

  def usingClient[F[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker,
      options: SttpBackendOptions,
      client: OkHttpClient,
      customEncodingHandler: EncodingHandler,
      webSocketBufferCapacity: Option[Int],
      bufferSize: Int
  ): SttpBackend[F, Fs2Streams[F] with WebSockets] = {
    OkHttpFs2Backend(
      client,
      blocker,
      closeClient = false,
      customEncodingHandler,
      webSocketBufferCapacity,
      bufferSize
    )
  }

  def resourceUsingClient[F[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      client: OkHttpClient,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity,
      bufferSize: Int = BUFFER_SIZE
  ): Resource[F, SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    Resource.make(
      Sync[F].delay(
        OkHttpFs2Backend(
          client,
          blocker,
          closeClient = false,
          customEncodingHandler,
          webSocketBufferCapacity,
          bufferSize
        )
      )
    )(_.close())

  def stub[F[_]: Concurrent]: SttpBackendStub[F, Fs2Streams[F] with WebSockets] = SttpBackendStub(
    new CatsMonadAsyncError[F]
  )

}
