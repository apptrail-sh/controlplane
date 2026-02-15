package sh.apptrail.controlplane.infrastructure.config

import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig {

  @Bean
  @ConditionalOnProperty(
    prefix = "apptrail.auth",
    name = ["enabled"],
    havingValue = "false",
    matchIfMissing = true,
  )
  fun disabledSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
      .csrf { it.disable() }
      .authorizeHttpRequests { it.anyRequest().permitAll() }
    return http.build()
  }

  @Bean
  @ConditionalOnProperty(
    prefix = "apptrail.auth",
    name = ["enabled"],
    havingValue = "true",
  )
  fun enabledSecurityFilterChain(
    http: HttpSecurity,
    jwtAuthenticationFilter: JwtAuthenticationFilter,
    apiKeyAuthenticationFilter: ApiKeyAuthenticationFilter,
  ): SecurityFilterChain {
    http
      .csrf { it.disable() }
      .cors(Customizer.withDefaults())
      .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
      .exceptionHandling { exceptions ->
        exceptions.authenticationEntryPoint { _, response, _ ->
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
        }
      }
      .authorizeHttpRequests { auth ->
        auth
          .requestMatchers("/health", "/actuator/**").permitAll()
          .requestMatchers("/api/v1/auth/**").permitAll()
          .requestMatchers("/webhooks/**").permitAll()
          .requestMatchers("/ingest/v1/**").hasAnyRole("USER", "API_KEY")
          .requestMatchers("/api/v1/**").hasRole("USER")
          .anyRequest().permitAll()
      }
      .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
      .addFilterAfter(jwtAuthenticationFilter, ApiKeyAuthenticationFilter::class.java)
    return http.build()
  }
}
