package sh.apptrail.controlplane.application.service.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import sh.apptrail.controlplane.infrastructure.config.AuthProperties
import sh.apptrail.controlplane.infrastructure.config.GoogleOidcProperties
import sh.apptrail.controlplane.infrastructure.config.JwtProperties
import sh.apptrail.controlplane.infrastructure.persistence.entity.UserEntity

class JwtTokenServiceTests {

  private val authProperties = AuthProperties(
    enabled = true,
    jwt = JwtProperties(
      secret = "test-secret-that-is-long-enough-for-hmac-sha-256-key",
      expirationSeconds = 3600,
      refreshExpirationSeconds = 604800,
      issuer = "apptrail-test",
    ),
    google = GoogleOidcProperties(clientId = "test"),
  )

  private val jwtTokenService = JwtTokenService(authProperties)

  private fun createTestUser(): UserEntity {
    return UserEntity().apply {
      id = 1L
      email = "test@example.com"
      name = "Test User"
      pictureUrl = "https://example.com/photo.jpg"
      provider = "google"
      providerSub = "12345"
    }
  }

  @Test
  fun `should generate and validate access token`() {
    val user = createTestUser()

    val token = jwtTokenService.generateAccessToken(user)
    assertThat(token).isNotBlank()

    val claims = jwtTokenService.validateToken(token)
    assertThat(claims).isNotNull
    assertThat(claims!!.subject).isEqualTo("1")
    assertThat(claims["email"]).isEqualTo("test@example.com")
    assertThat(claims["name"]).isEqualTo("Test User")
    assertThat(claims["type"]).isEqualTo("access")
    assertThat(jwtTokenService.isRefreshToken(claims)).isFalse()
  }

  @Test
  fun `should generate and validate refresh token`() {
    val user = createTestUser()

    val token = jwtTokenService.generateRefreshToken(user)
    assertThat(token).isNotBlank()

    val claims = jwtTokenService.validateToken(token)
    assertThat(claims).isNotNull
    assertThat(claims!!.subject).isEqualTo("1")
    assertThat(claims["type"]).isEqualTo("refresh")
    assertThat(jwtTokenService.isRefreshToken(claims)).isTrue()
  }

  @Test
  fun `should return null for invalid token`() {
    val claims = jwtTokenService.validateToken("invalid.token.here")
    assertThat(claims).isNull()
  }

  @Test
  fun `should return null for token with wrong secret`() {
    val otherProperties = authProperties.copy(
      jwt = authProperties.jwt.copy(secret = "different-secret-that-is-long-enough-for-hmac-key")
    )
    val otherService = JwtTokenService(otherProperties)

    val user = createTestUser()
    val token = otherService.generateAccessToken(user)

    val claims = jwtTokenService.validateToken(token)
    assertThat(claims).isNull()
  }
}
