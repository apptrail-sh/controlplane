package sh.apptrail.controlplane.infrastructure.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import sh.apptrail.controlplane.application.service.auth.ApiKeyService

@Component
@ConditionalOnProperty(prefix = "apptrail.auth", name = ["enabled"], havingValue = "true")
class ApiKeyAuthenticationFilter(
  private val apiKeyService: ApiKeyService,
) : OncePerRequestFilter() {

  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain,
  ) {
    val apiKey = request.getHeader("X-API-Key")

    if (apiKey != null) {
      val keyEntity = apiKeyService.validateKey(apiKey)
      if (keyEntity != null) {
        val auth = UsernamePasswordAuthenticationToken(
          "api-key:${keyEntity.name}",
          null,
          listOf(SimpleGrantedAuthority("ROLE_API_KEY")),
        )
        SecurityContextHolder.getContext().authentication = auth
      }
    }

    filterChain.doFilter(request, response)
  }
}
