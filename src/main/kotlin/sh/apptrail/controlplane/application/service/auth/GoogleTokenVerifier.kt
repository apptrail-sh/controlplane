package sh.apptrail.controlplane.application.service.auth

data class GoogleClaims(
  val sub: String,
  val email: String,
  val name: String?,
  val picture: String?,
  val emailVerified: Boolean,
)

class AuthException(message: String) : RuntimeException(message)
