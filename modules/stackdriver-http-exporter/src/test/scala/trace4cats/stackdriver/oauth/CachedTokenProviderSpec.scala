package trace4cats.stackdriver.oauth

import cats.effect.IO
import cats.effect.testkit.{TestControl, TestInstances}
import cats.effect.unsafe.implicits.global
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.duration._

class CachedTokenProviderSpec extends AnyFlatSpec with Matchers with ScalaCheckDrivenPropertyChecks with TestInstances {
  implicit val longArb: Arbitrary[Long] = Arbitrary(Gen.posNum[Long])

  implicit val accessTokenArb: Arbitrary[AccessToken] = Arbitrary(for {
    token <- Gen.alphaNumStr
    expires <- Gen.chooseNum(0L, 3600L)
  } yield AccessToken(token, "bearer", expires))

  it should "return a cached token when clock tick is less than expiry" in forAll {
    (token1: AccessToken, token2: AccessToken) =>
      val updatedToken1 = token1.copy(expires_in = 2)
      val provider = testTokenProvider(updatedToken1, token2)

      val test = for {
        cached <- CachedTokenProvider[IO](provider, 0.seconds)
        first <- cached.accessToken
        _ <- IO.sleep(1.second)
        second <- cached.accessToken
      } yield {
        first.copy(expires_in = 1) should be(second)
        first should be(updatedToken1)
        if (token1.access_token != token2.access_token) first.access_token should not be (token2.access_token)
        ()
      }

      TestControl.executeEmbed(test).unsafeRunSync()
  }

  it should "return a new token when clock tick is greater than expiry" in forAll {
    (token1: AccessToken, token2: AccessToken) =>
      val updatedToken1 = token1.copy(expires_in = 1)
      val provider = testTokenProvider(updatedToken1, token2)

      val test = for {
        cached <- CachedTokenProvider[IO](provider, 0.seconds)
        first <- cached.accessToken
        _ <- IO.sleep(2.seconds)
        second <- cached.accessToken
      } yield {
        first should be(updatedToken1)
        second should be(token2)
        ()
      }

      TestControl.executeEmbed(test).unsafeRunSync()
  }

  def testTokenProvider(first: AccessToken, second: AccessToken): TokenProvider[IO] =
    new TokenProvider[IO] {
      var invCount = 0

      override val accessToken: IO[AccessToken] = IO {
        val token =
          if (invCount == 0) first
          else second

        invCount = invCount + 1
        token
      }
    }
}
