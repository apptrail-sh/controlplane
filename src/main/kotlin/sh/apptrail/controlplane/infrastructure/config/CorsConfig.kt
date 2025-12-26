package sh.apptrail.controlplane.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@ConfigurationProperties(prefix = "app.cors")
data class CorsProperties(
  val allowedOrigins: List<String> = emptyList(),
  val allowedMethods: List<String> = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
  val allowedHeaders: List<String> = listOf("*"),
  val allowCredentials: Boolean = false,
  val maxAge: Long = 3600,
)

@Configuration
@EnableConfigurationProperties(CorsProperties::class)
class CorsConfig(
  private val properties: CorsProperties,
) : WebMvcConfigurer {
  override fun addCorsMappings(registry: CorsRegistry) {
    val mapping = registry.addMapping("/**")
      .allowedMethods(*properties.allowedMethods.toTypedArray())
      .allowedHeaders(*properties.allowedHeaders.toTypedArray())
      .allowCredentials(properties.allowCredentials)
      .maxAge(properties.maxAge)

    if (properties.allowedOrigins.isNotEmpty()) {
      mapping.allowedOrigins(*properties.allowedOrigins.toTypedArray())
    }
  }
}
