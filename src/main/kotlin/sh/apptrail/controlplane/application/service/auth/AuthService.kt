package sh.apptrail.controlplane.application.service.auth

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sh.apptrail.controlplane.infrastructure.config.AuthProperties
import sh.apptrail.controlplane.infrastructure.persistence.entity.UserEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.UserRepository
import java.time.Instant

data class AuthTokens(
  val accessToken: String,
  val refreshToken: String,
  val expiresIn: Long,
  val tokenType: String = "Bearer",
)

@Service
@ConditionalOnProperty(prefix = "apptrail.auth", name = ["enabled"], havingValue = "true")
class AuthService(
  private val googleAuthCodeExchanger: GoogleAuthCodeExchanger,
  private val jwtTokenService: JwtTokenService,
  private val userRepository: UserRepository,
  private val authProperties: AuthProperties,
) {

  private val log = LoggerFactory.getLogger(AuthService::class.java)

  @Transactional
  fun authenticateWithGoogle(code: String): AuthTokens {
    val claims = googleAuthCodeExchanger.exchangeAuthCode(code)

    // Check domain restriction
    if (authProperties.allowedDomains.isNotEmpty()) {
      val domain = claims.email.substringAfter('@')
      if (domain !in authProperties.allowedDomains) {
        log.warn("Login rejected: email domain '{}' not in allowed domains", domain)
        throw AuthException("Email domain not allowed")
      }
    }

    // Find or create user
    val user = userRepository.findByProviderAndProviderSub("google", claims.sub)
      ?: userRepository.findByEmail(claims.email)?.apply {
        // Existing user with same email but different Google sub — migrate account
        log.info("Migrating user account for email={} to new Google sub", claims.email)
        providerSub = claims.sub
      }
      ?: UserEntity().apply {
        email = claims.email
        provider = "google"
        providerSub = claims.sub
      }

    // Update user info
    user.name = claims.name
    user.pictureUrl = claims.picture
    user.lastLoginAt = Instant.now()
    val savedUser = userRepository.save(user)

    log.info("User authenticated: email={}", savedUser.email)

    return AuthTokens(
      accessToken = jwtTokenService.generateAccessToken(savedUser),
      refreshToken = jwtTokenService.generateRefreshToken(savedUser),
      expiresIn = authProperties.jwt.expirationSeconds,
    )
  }

  @Transactional
  fun refreshToken(refreshToken: String): AuthTokens {
    val claims = jwtTokenService.validateToken(refreshToken)
      ?: throw AuthException("Invalid refresh token")

    if (!jwtTokenService.isRefreshToken(claims)) {
      throw AuthException("Token is not a refresh token")
    }

    val userId = claims.subject.toLongOrNull()
      ?: throw AuthException("Invalid token subject")

    val user = userRepository.findById(userId).orElse(null)
      ?: throw AuthException("User not found")

    // Validate token version — rejects old refresh tokens after rotation
    val tokenVersion = (claims["tokenVersion"] as? Number)?.toInt()
    if (tokenVersion == null || tokenVersion != user.tokenVersion) {
      throw AuthException("Refresh token has been revoked")
    }

    // Increment token version to invalidate the current refresh token
    user.tokenVersion = user.tokenVersion + 1
    userRepository.save(user)

    return AuthTokens(
      accessToken = jwtTokenService.generateAccessToken(user),
      refreshToken = jwtTokenService.generateRefreshToken(user),
      expiresIn = authProperties.jwt.expirationSeconds,
    )
  }
}
