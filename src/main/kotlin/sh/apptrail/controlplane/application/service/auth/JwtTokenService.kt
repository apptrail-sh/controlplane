package sh.apptrail.controlplane.application.service.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.config.AuthProperties
import sh.apptrail.controlplane.infrastructure.persistence.entity.UserEntity
import java.util.Date
import javax.crypto.SecretKey

@Service
@ConditionalOnProperty(prefix = "apptrail.auth", name = ["enabled"], havingValue = "true")
class JwtTokenService(
  private val authProperties: AuthProperties,
) {

  private val signingKey: SecretKey by lazy {
    val secret = authProperties.jwt.secret
    require(secret.isNotBlank()) { "apptrail.auth.jwt.secret must be configured when auth is enabled" }
    Keys.hmacShaKeyFor(secret.toByteArray())
  }

  fun generateAccessToken(user: UserEntity): String {
    val now = Date()
    val expiry = Date(now.time + authProperties.jwt.expirationSeconds * 1000)

    return Jwts.builder()
      .subject(user.id.toString())
      .claim("email", user.email)
      .claim("name", user.name)
      .claim("picture", user.pictureUrl)
      .claim("type", "access")
      .issuer(authProperties.jwt.issuer)
      .issuedAt(now)
      .expiration(expiry)
      .signWith(signingKey)
      .compact()
  }

  fun generateRefreshToken(user: UserEntity): String {
    val now = Date()
    val expiry = Date(now.time + authProperties.jwt.refreshExpirationSeconds * 1000)

    return Jwts.builder()
      .subject(user.id.toString())
      .claim("type", "refresh")
      .claim("tokenVersion", user.tokenVersion)
      .issuer(authProperties.jwt.issuer)
      .issuedAt(now)
      .expiration(expiry)
      .signWith(signingKey)
      .compact()
  }

  fun validateToken(token: String): Claims? {
    return try {
      Jwts.parser()
        .verifyWith(signingKey)
        .requireIssuer(authProperties.jwt.issuer)
        .build()
        .parseSignedClaims(token)
        .payload
    } catch (e: JwtException) {
      null
    } catch (e: IllegalArgumentException) {
      null
    }
  }

  fun isRefreshToken(claims: Claims): Boolean {
    return claims["type"] == "refresh"
  }
}
