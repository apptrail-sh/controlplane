package sh.apptrail.controlplane.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.clusters")
data class ClusterEnvironmentProperties(
  val definitions: Map<String, ClusterDefinition> = emptyMap(),
  val environments: List<EnvironmentDefinition> = emptyList(),
)

data class ClusterDefinition(
  val environment: String,
  val alias: String? = null,
  val shard: ShardConfig? = null,
  val namespaces: Map<String, NamespaceConfig> = emptyMap(),
)

data class NamespaceConfig(
  val shard: ShardConfig? = null,
)

data class ShardConfig(
  val name: String,
  val order: Int,
)

data class EnvironmentDefinition(
  val name: String,
  val order: Int,
  val metadata: Map<String, String> = emptyMap(),
)
