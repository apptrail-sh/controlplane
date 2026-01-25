package sh.apptrail.controlplane.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.clusters")
data class ClusterTopologyProperties(
  val definitions: Map<String, ClusterConfig> = emptyMap(),
  val environments: List<EnvironmentConfig> = emptyList(),
)

data class ClusterConfig(
  val environment: String,
  val alias: String? = null,
  val cell: String? = null,
  val namespaces: Map<String, NamespaceConfig> = emptyMap(),
)

data class NamespaceConfig(
  val environment: String? = null,
  val cell: String? = null,
)

data class CellDefinition(
  val name: String,
  val order: Int,
  val alias: String? = null,
)

data class EnvironmentConfig(
  val name: String,
  val order: Int,
  val metadata: Map<String, String> = emptyMap(),
  val cells: List<CellDefinition> = emptyList(),
)
