package sh.apptrail.controlplane.infrastructure.alerting.prometheus

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "apptrail.alerting.prometheus")
data class PrometheusProperties(
  val enabled: Boolean = false,
  val baseUrl: String = "http://localhost:8481",
  val queryPath: String = "/select/0:0/prometheus/api/v1/query",
  val timeoutMs: Long = 5000,
  val cache: CacheProperties = CacheProperties(),
)

data class CacheProperties(
  val enabled: Boolean = true,
  val ttlSeconds: Long = 30,
)
