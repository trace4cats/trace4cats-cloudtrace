package io.janstenpickle.trace4cats.stackdriver.oauth

/** Code adapted from https://github.com/permutive/fs2-google-pubsub
  */
import java.io.File
import cats.MonadThrow
import cats.effect.kernel.{Async, Clock, Sync}
import cats.syntax.all._
import org.http4s.client.Client
import org.typelevel.log4cats.Logger

class OAuthTokenProvider[F[_]: MonadThrow: Clock](emailAddress: String, scope: List[String], auth: OAuth[F])
    extends TokenProvider[F] {
  override val accessToken: F[AccessToken] = {
    for {
      now <- Clock[F].realTimeInstant
      token <- auth.authenticate(emailAddress, scope.mkString(","), now.plusMillis(auth.maxDuration.toMillis), now)
      tokenOrError <- token.liftTo[F](TokenProvider.FailedToGetToken)
    } yield tokenOrError
  }
}

object OAuthTokenProvider {
  def apply[F[_]: Async: Logger](serviceAccountPath: String, httpClient: Client[F]): F[OAuthTokenProvider[F]] =
    for {
      path <- Sync[F].delay(new File(serviceAccountPath).toPath)
      serviceAccount <- GoogleAccountParser.parse(path)
    } yield new OAuthTokenProvider(
      serviceAccount.clientEmail,
      List("https://www.googleapis.com/auth/trace.append"),
      new GoogleOAuth(serviceAccount.privateKey, httpClient)
    )

  def noAuth[F[_]: Sync]: OAuthTokenProvider[F] =
    new OAuthTokenProvider("noop", Nil, new NoopOAuth)
}
