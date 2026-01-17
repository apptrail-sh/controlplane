package sh.apptrail.controlplane.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.clusters")
data class ClusterEnvironmentProperties(
  val environments: Map<String, String> = emptyMap(),
  val environmentOrder: List<String> = listOf("dev", "staging", "production"),
)
