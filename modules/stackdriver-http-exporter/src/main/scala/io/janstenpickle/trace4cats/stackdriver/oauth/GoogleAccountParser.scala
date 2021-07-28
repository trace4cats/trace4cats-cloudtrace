package io.janstenpickle.trace4cats.stackdriver.oauth

/** Code adapted from https://github.com/permutive/fs2-google-pubsub
  */
import java.nio.file.{Files, Path}
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.regex.Pattern

import cats.syntax.either._
import io.circe.Codec
import io.circe.generic.semiauto._

object GoogleAccountParser {
  case class JsonGoogleServiceAccount(
    `type`: String,
    project_id: String,
    private_key_id: String,
    private_key: String,
    client_email: String,
    auth_uri: String
  )

  object JsonGoogleServiceAccount {
    implicit final val codec: Codec[JsonGoogleServiceAccount] = deriveCodec
  }

  final def parse(path: Path): Either[Throwable, GoogleServiceAccount] =
    for {
      string <- Either.catchNonFatal(new String(Files.readAllBytes(path)))
      json <- io.circe.parser.parse(string)
      serviceAccount <- json.as[JsonGoogleServiceAccount]
      gsa <- Either.catchNonFatal {
        val spec = new PKCS8EncodedKeySpec(loadPem(serviceAccount.private_key))
        val kf = KeyFactory.getInstance("RSA")
        GoogleServiceAccount(
          clientEmail = serviceAccount.client_email,
          privateKey = kf.generatePrivate(spec).asInstanceOf[RSAPrivateKey]
        )
      }
    } yield gsa

  final private[this] val privateKeyPattern = Pattern.compile("(?m)(?s)^---*BEGIN.*---*$(.*)^---*END.*---*$.*")

  private def loadPem(pem: String): Array[Byte] = {
    val encoded = privateKeyPattern.matcher(pem).replaceFirst("$1")
    Base64.getMimeDecoder.decode(encoded)
  }
}

case class GoogleServiceAccount(clientEmail: String, privateKey: RSAPrivateKey)
