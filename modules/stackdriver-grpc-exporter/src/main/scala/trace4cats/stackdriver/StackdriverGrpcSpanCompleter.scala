package trace4cats.stackdriver

import cats.effect.kernel.{Async, Resource}
import com.google.auth.Credentials
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import trace4cats.kernel.SpanCompleter
import trace4cats.model._
import trace4cats.{CompleterConfig, QueuedSpanCompleter}

import scala.concurrent.duration._

object StackdriverGrpcSpanCompleter {
  def apply[F[_]: Async](
    process: TraceProcess,
    projectId: String,
    credentials: Option[Credentials] = None,
    requestTimeout: FiniteDuration = 5.seconds,
    config: CompleterConfig = CompleterConfig()
  ): Resource[F, SpanCompleter[F]] =
    Resource.eval(Slf4jLogger.create[F]).flatMap { implicit logger: Logger[F] =>
      StackdriverGrpcSpanExporter[F, Chunk](projectId, credentials, requestTimeout)
        .flatMap(QueuedSpanCompleter[F](process, _, config))
    }
}
