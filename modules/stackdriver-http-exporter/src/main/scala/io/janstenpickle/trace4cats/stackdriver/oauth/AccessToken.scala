package io.janstenpickle.trace4cats.stackdriver.oauth

/** Code adapted from https://github.com/permutive/fs2-google-pubsub
  */
import io.circe.Codec
import io.circe.generic.semiauto._

final case class AccessToken(access_token: String, token_type: String, expires_in: Long)

object AccessToken {
  implicit val codec: Codec[AccessToken] = deriveCodec
}
