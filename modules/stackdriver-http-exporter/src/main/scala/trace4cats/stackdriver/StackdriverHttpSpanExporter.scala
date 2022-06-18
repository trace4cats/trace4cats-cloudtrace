package trace4cats.stackdriver

import cats.Foldable
import cats.effect.kernel.{Async, Temporal}
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import org.http4s.Uri
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import trace4cats.HttpSpanExporter
import trace4cats.kernel.SpanExporter
import trace4cats.model.Batch
import trace4cats.stackdriver.oauth.{
  CachedTokenProvider,
  InstanceMetadataTokenProvider,
  OAuthTokenProvider,
  TokenProvider
}
import trace4cats.stackdriver.project.{InstanceMetadataProjectIdProvider, ProjectIdProvider, StaticProjectIdProvider}

import scala.collection.mutable.ListBuffer

object StackdriverHttpSpanExporter {
  private final val base = "https://cloudtrace.googleapis.com/v2/projects"

  def apply[F[_]: Async: Logger, G[_]: Foldable](
    projectId: String,
    serviceAccountPath: String,
    client: Client[F]
  ): F[SpanExporter[F, G]] =
    OAuthTokenProvider[F](serviceAccountPath, client).flatMap { tokenProvider =>
      apply[F, G](StaticProjectIdProvider(projectId), tokenProvider, client)
    }

  def apply[F[_]: Temporal: Logger, G[_]: Foldable](
    client: Client[F],
    serviceAccountName: String = "default"
  ): F[SpanExporter[F, G]] =
    apply[F, G](
      InstanceMetadataProjectIdProvider(client),
      InstanceMetadataTokenProvider(client, serviceAccountName),
      client
    )

  def apply[F[_]: Temporal, G[_]: Foldable](
    projectIdProvider: ProjectIdProvider[F],
    tokenProvider: TokenProvider[F],
    client: Client[F]
  ): F[SpanExporter[F, G]] =
    for {
      cachedTokenProvider <- CachedTokenProvider(tokenProvider)
      projectId <- projectIdProvider.projectId
      uri <- Uri.fromString(s"$base/$projectId/traces:batchWrite").liftTo[F]
    } yield HttpSpanExporter[F, G, model.Batch](
      client,
      uri,
      (batch: Batch[G]) =>
        model.Batch(
          batch.spans
            .foldLeft(ListBuffer.empty[model.Span]) { (buf, span) =>
              buf += model.Span.fromCompleted(projectId, span)
            }
            .toList
        ),
      (uri: Uri) =>
        cachedTokenProvider.accessToken.map { token =>
          uri.withQueryParam("access_token", token.access_token)
        }
    )
}
