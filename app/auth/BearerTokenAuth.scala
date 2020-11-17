package auth

import java.time.Instant
import java.util.Date

import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import javax.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory
import play.api.mvc.RequestHeader
import play.api.Configuration
import play.api.libs.typedmap.TypedKey
import scala.jdk.CollectionConverters._

import scala.io.Source
import scala.util.{Failure, Success, Try}

object BearerTokenAuth {
  final val ClaimsAttributeKey = TypedKey[JWTClaimsSet]("claims")
}

object ClaimsSetExtensions {
  implicit class ExtendedClaimsSet(val s:JWTClaimsSet) extends AnyVal {
    def getAzp:Option[String] = Option(s.getClaim("azp")).map(_.asInstanceOf[String])
  }
}

/**
 * this class implements bearer token authentication. It's injectable because it needs to access app config.
 * You don't need to integrate it directly in your controller, it is required by the Security trait.
 *
 * This expects there to be an `auth` section in the application.conf which should contain two keys:
 * auth {
 *   adminClaim = "claim-field-indicating-admin"
 *   tokenSigningCert = """----- BEGIN CERTIFICATE -----
 *   {your certificate....}
 *   ----- END CERTIFICATE -----"""
 * }
 *
 * A given bearer token must authenticate against the provided certificate to be allowed access, and its expiry time
 * must not be in the past. The token's subject field ("sub") is used as the username.
 * Admin access is only granted if the token's field given by auth.adminClaim is a string that equates to "true" or "yes".
 *
 * So, in order to use it:
 *
 * class MyController @Inject() (controllerComponents:ControllerComponents, override bearerTokenAuth:BearerTokenAuth) extends AbstractController(controllerComponets) with Security { }
 * @param config application configuration object. This is normally provided by the injector
 */
@Singleton
class BearerTokenAuth @Inject() (config:Configuration) {
  import ClaimsSetExtensions._
  private val logger = LoggerFactory.getLogger(getClass)

  //see https://stackoverflow.com/questions/475074/regex-to-parse-or-validate-base64-data
  //it is not the best option but is the simplest that will work here
  private val authXtractor = "^Bearer\\s+([a-zA-Z0-9+/._-]*={0,3})$".r
  private val maybeVerifier = loadInKey() match {
    case Failure(err)=>
      if(!sys.env.contains("CI")) logger.warn(s"No token validation cert in config so bearer token auth will not work. Error was ${err.getMessage}")
      None
    case Success(jwk)=>
      Some(new RSASSAVerifier(jwk.toRSAKey))
  }

  /**
   * returns the configured name for the claim field that will give whether a user is an admin or not.
   * It's included here because the Security trait is a mixin and can't access the config directly.
   * @return
   */
  def isAdminClaimName():String = {
    config.get[String]("auth.adminClaim")
  }

  /**
   * extracts the authorization token from the provided header
   * @param fromString complete Authorization header text
   * @return None if the header text does not match the expected format. The raw bearer token if it does.
   */
  def extractAuthorization(fromString:String):Either[LoginResult,LoginResultOK[String]] =
    fromString match {
      case authXtractor(token)=>
        logger.debug("found valid base64 bearer")
        Right(LoginResultOK(token))
      case _=>
        logger.warn("no bearer token found or it failed to validate")
        Left(LoginResultInvalid("no token"))
    }

  /**
   * loads in the public certificate used for validating the bearer tokens from configuration
   * @return either the passed JWK object or a Failure indicating why it would not load.
   */
  def loadInKey() = Try {
    val pemCertLocation = config.get[String]("auth.tokenSigningCertPath")
    val s = Source.fromFile(pemCertLocation, "UTF-8")
    try {
      val pemCertData = s.getLines().reduce(_ + _)
      JWK.parseFromPEMEncodedX509Cert(pemCertData)
    } finally {
      s.close()
    }
  }

  /**
   * try to validate the given token with the key provided
   * returns a JWTClaimsSet if successful
   * @param token JWT token to verify
   * @return a Try, containing a JWTClaimsSet or an error
   */
  def validateToken(token:LoginResultOK[String]):Either[LoginResult,LoginResultOK[JWTClaimsSet]] = {
    logger.debug(s"validating token $token")
    Try {
      SignedJWT.parse(token.content)
    } match {
      case Success(signedJWT) =>
        maybeVerifier match {
          case Some(verifier) =>
            if (signedJWT.verify(verifier)) {
              logger.debug("verified JWT")
              logger.debug(s"${signedJWT.getJWTClaimsSet.toJSONObject(true).toJSONString}")

              val claimsSet = signedJWT.getJWTClaimsSet
              val audiences = claimsSet.getAudience.asScala ++ claimsSet.getAzp
              logger.info(s"JWT audiences: $audiences")
              config.getOptional[Seq[String]]("auth.validAudiences") match {
                case None=>
                  logger.error(s"No valid audiences configured. Set auth.validAudiences. Token audiences were $audiences")
                  Left(LoginResultMisconfigured("Server configuration problem"))
                case Some(audienceList)=>
                  if(audiences.intersect(audienceList).nonEmpty) {
                    logger.debug("Audience permitted")
                    Right(LoginResultOK(signedJWT.getJWTClaimsSet))
                  } else {
                    Left(LoginResultInvalid("Invalid audience"))
                  }
              }
            } else {
              Left(LoginResultInvalid(token.content))
            }
          case None =>
            Left(LoginResultMisconfigured("No signing cert configured"))
        }
      case Failure(err) =>
        logger.error(s"Failed to validate token for ${token.content}: ${err.getMessage}")
        Left(LoginResultInvalid(token.content))
    }
  }

  /**
   * check the given parsed claims set to see if the token has already expired
   * @param claims JWTClaimsSet representing the token under consideration
   * @return a Try, containing either the claims set or a failure indicating the reason authentication failed. This is
   *         to make composition easier.
   */
  def checkExpiry(claims:JWTClaimsSet):Either[LoginResult,LoginResultOK[JWTClaimsSet]] = {
    if(claims.getExpirationTime.before(Date.from(Instant.now()))) {
      logger.debug(s"JWT was valid but expired at ${claims.getExpirationTime.formatted("YYYY-MM-dd HH:mm:ss")}")
      Left(LoginResultExpired(claims.getSubject))
    } else {
      Right(LoginResultOK(claims))
    }
  }

  /**
   * performs the JWT authentication against a given header.
   * This should not be called directly, but is done in the Security trait as part of IsAuthenticated or IsAdmin.
   * @param rh request header
   * @return a LoginResult subclass, as a Left if something failed or a Right if it succeeded
   */
  def apply(rh: RequestHeader): Either[LoginResult,LoginResultOK[JWTClaimsSet]] = {
    rh.headers.get("Authorization") match {
      case Some(authValue)=>
        extractAuthorization(authValue)
          .flatMap(validateToken)
          .flatMap(result=>checkExpiry(result.content))
          .map(result=>{
            rh.addAttr(BearerTokenAuth.ClaimsAttributeKey, result.content)
            result
          })
      case None=>
        logger.error("Attempt to access without authorization")
        Left(LoginResultNotPresent)
    }
  }
}
