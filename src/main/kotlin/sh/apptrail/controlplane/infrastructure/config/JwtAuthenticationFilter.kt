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
import sh.apptrail.controlplane.application.service.auth.JwtTokenService

@Component
@ConditionalOnProperty(prefix = "apptrail.auth", name = ["enabled"], havingValue = "true")
class JwtAuthenticationFilter(
  private val jwtTokenService: JwtTokenService,
) : OncePerRequestFilter() {

  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain,
  ) {
    // Skip if already authenticated (e.g., by API key filter)
    if (SecurityContextHolder.getContext().authentication != null) {
      filterChain.doFilter(request, response)
      return
    }

    val header = request.getHeader("Authorization")
    if (header != null && header.startsWith("Bearer ")) {
      val token = header.substring(7)
      val claims = jwtTokenService.validateToken(token)

      if (claims != null && !jwtTokenService.isRefreshToken(claims)) {
        val auth = UsernamePasswordAuthenticationToken(
          claims.subject,
          null,
          listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
        auth.details = mapOf(
          "email" to claims["email"],
          "name" to claims["name"],
          "picture" to claims["picture"],
        )
        SecurityContextHolder.getContext().authentication = auth
      }
    }

    filterChain.doFilter(request, response)
  }
}
