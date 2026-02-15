package sh.apptrail.controlplane.web.controller

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.application.service.auth.AuthException
import sh.apptrail.controlplane.application.service.auth.AuthService
import sh.apptrail.controlplane.application.service.auth.AuthTokens

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "apptrail.auth", name = ["enabled"], havingValue = "true")
class AuthController(
  private val authService: AuthService,
) {

  @PostMapping("/google")
  fun loginWithGoogle(@RequestBody request: GoogleLoginRequest): ResponseEntity<AuthTokensResponse> {
    return try {
      val tokens = authService.authenticateWithGoogle(request.code)
      ResponseEntity.ok(tokens.toResponse())
    } catch (e: AuthException) {
      ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }
  }

  @PostMapping("/refresh")
  fun refreshToken(@RequestBody request: RefreshTokenRequest): ResponseEntity<AuthTokensResponse> {
    return try {
      val tokens = authService.refreshToken(request.refreshToken)
      ResponseEntity.ok(tokens.toResponse())
    } catch (e: AuthException) {
      ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
    }
  }

  @GetMapping("/me")
  fun getCurrentUser(): ResponseEntity<UserInfoResponse> {
    val auth = SecurityContextHolder.getContext().authentication ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

    @Suppress("UNCHECKED_CAST")
    val details = auth.details as? Map<String, Any?> ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

    return ResponseEntity.ok(
      UserInfoResponse(
        id = auth.principal as String,
        email = details["email"] as? String,
        name = details["name"] as? String,
        picture = details["picture"] as? String,
      )
    )
  }
}

data class GoogleLoginRequest(val code: String)
data class RefreshTokenRequest(val refreshToken: String)

data class AuthTokensResponse(
  val accessToken: String,
  val refreshToken: String,
  val expiresIn: Long,
  val tokenType: String,
)

data class UserInfoResponse(
  val id: String,
  val email: String?,
  val name: String?,
  val picture: String?,
)

private fun AuthTokens.toResponse() = AuthTokensResponse(
  accessToken = accessToken,
  refreshToken = refreshToken,
  expiresIn = expiresIn,
  tokenType = tokenType,
)
