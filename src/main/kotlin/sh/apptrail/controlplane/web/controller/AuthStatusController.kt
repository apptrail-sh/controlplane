package sh.apptrail.controlplane.web.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.infrastructure.config.AuthProperties

@RestController
@RequestMapping("/api/v1/auth")
class AuthStatusController(
  private val authProperties: AuthProperties,
) {

  @GetMapping("/status")
  fun getAuthStatus(): AuthStatusResponse {
    val providers = if (authProperties.enabled && authProperties.google.clientId.isNotBlank()) {
      listOf("google")
    } else {
      emptyList()
    }

    return AuthStatusResponse(
      enabled = authProperties.enabled,
      providers = providers,
      googleClientId = if (authProperties.enabled) authProperties.google.clientId.ifBlank { null } else null,
    )
  }
}

data class AuthStatusResponse(
  val enabled: Boolean,
  val providers: List<String>,
  val googleClientId: String?,
)
