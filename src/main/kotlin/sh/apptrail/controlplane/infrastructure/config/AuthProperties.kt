package sh.apptrail.controlplane.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "apptrail.auth")
data class AuthProperties(
  val enabled: Boolean = false,
  val jwt: JwtProperties = JwtProperties(),
  val google: GoogleOidcProperties = GoogleOidcProperties(),
  val allowedDomains: List<String> = emptyList(),
  val seedApiKey: String? = null,
)

data class JwtProperties(
  val secret: String = "",
  val expirationSeconds: Long = 3600,
  val refreshExpirationSeconds: Long = 604800,
  val issuer: String = "apptrail",
)

data class GoogleOidcProperties(
  val clientId: String = "",
  val clientSecret: String = "",
)
