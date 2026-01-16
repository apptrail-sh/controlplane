package sh.apptrail.controlplane.infrastructure.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@ConfigurationProperties(prefix = "app.prometheus")
data class PrometheusProperties(
  val enabled: Boolean = false,
  val baseUrl: String = "http://localhost:8481",
  val apiPath: String = "/select/0/prometheus/api/v1",
  val timeoutSeconds: Long = 30
)

@Configuration
@EnableConfigurationProperties(PrometheusProperties::class)
class PrometheusConfig(
  private val properties: PrometheusProperties
) {

  @Bean
  @ConditionalOnProperty(prefix = "app.prometheus", name = ["enabled"], havingValue = "true")
  fun prometheusRestClient(): RestClient {
    val timeout = Duration.ofSeconds(properties.timeoutSeconds)
    val requestFactory = SimpleClientHttpRequestFactory().apply {
      setConnectTimeout(timeout)
      setReadTimeout(timeout)
    }

    return RestClient.builder()
      .baseUrl(properties.baseUrl + properties.apiPath)
      .requestFactory(requestFactory)
      .build()
  }
}
