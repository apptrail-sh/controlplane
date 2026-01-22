package sh.apptrail.controlplane.infrastructure.config

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "apptrail.metrics-queries")
data class MetricsQueriesProperties(
  val enabled: Boolean = false,
  val sparklinesEnabled: Boolean = false,
  val queries: List<MetricQueryConfig> = emptyList(),
)

enum class MetricUnit {
  @JsonProperty("percentage")
  PERCENTAGE,
  @JsonProperty("requests_per_second")
  REQUESTS_PER_SECOND,
  @JsonProperty("milliseconds")
  MILLISECONDS,
  @JsonProperty("seconds")
  SECONDS,
  @JsonProperty("count")
  COUNT,
  @JsonProperty("bytes")
  BYTES,
}

enum class MetricCategory {
  @JsonProperty("errors")
  ERRORS,
  @JsonProperty("traffic")
  TRAFFIC,
  @JsonProperty("latency")
  LATENCY,
  @JsonProperty("saturation")
  SATURATION,
}

data class MetricQueryConfig(
  val id: String,
  val name: String,
  val description: String? = null,
  val query: String,
  val unit: MetricUnit = MetricUnit.COUNT,
  val category: MetricCategory = MetricCategory.TRAFFIC,
  val sparklineRange: String = "1h",
  val sparklineStep: String = "5m",
)
