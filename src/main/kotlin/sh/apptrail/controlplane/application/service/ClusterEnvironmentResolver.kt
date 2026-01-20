package sh.apptrail.controlplane.application.service

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.config.ClusterEnvironmentProperties

data class ShardInfo(
  val name: String,
  val order: Int,
)

@Service
@EnableConfigurationProperties(ClusterEnvironmentProperties::class)
class ClusterEnvironmentResolver(
  private val properties: ClusterEnvironmentProperties,
) {
  fun resolveEnvironment(clusterId: String): String {
    return properties.environments[clusterId] ?: "unknown"
  }

  /**
   * Resolves the shard for a given cluster and namespace.
   * Resolution order:
   * 1. First check for cluster.namespace specific mapping
   * 2. Fall back to cluster-level mapping
   * @return ShardInfo if a shard is configured, null otherwise
   */
  fun resolveShard(clusterId: String, namespace: String): ShardInfo? {
    // Try cluster.namespace-level mapping first
    val namespaceKey = "$clusterId.$namespace"
    val namespaceConfig = properties.shards[namespaceKey]
    if (namespaceConfig != null) {
      return ShardInfo(name = namespaceConfig.name, order = namespaceConfig.order)
    }

    // Fall back to cluster-level mapping
    val clusterConfig = properties.shards[clusterId]
    if (clusterConfig != null) {
      return ShardInfo(name = clusterConfig.name, order = clusterConfig.order)
    }

    return null
  }

  /**
   * Gets the order for a shard by name.
   * @return the shard order, or null if shard is not configured
   */
  fun getShardOrder(shardName: String): Int? {
    return properties.shards.values.find { it.name == shardName }?.order
  }

  /**
   * Gets all configured shards grouped by environment.
   * @return Map of environment name to list of ShardInfo
   */
  fun getShardsByEnvironment(): Map<String, List<ShardInfo>> {
    val result = mutableMapOf<String, MutableList<ShardInfo>>()

    // Build reverse mapping: cluster -> environment
    val clusterToEnv = properties.environments

    // For each shard config, find its environment
    for ((key, config) in properties.shards) {
      // Key can be either "clusterId" or "clusterId.namespace"
      val clusterId = key.split(".").first()
      val environment = clusterToEnv[clusterId] ?: continue

      val shardInfo = ShardInfo(name = config.name, order = config.order)
      result.getOrPut(environment) { mutableListOf() }.add(shardInfo)
    }

    // Sort shards by order and deduplicate by name
    return result.mapValues { (_, shards) ->
      shards.distinctBy { it.name }.sortedBy { it.order }
    }
  }

  /**
   * Gets all configured shards as a flat list (deduplicated by name).
   * @return List of ShardInfo sorted by order
   */
  fun getAllShards(): List<ShardInfo> {
    return properties.shards.values
      .map { ShardInfo(name = it.name, order = it.order) }
      .distinctBy { it.name }
      .sortedBy { it.order }
  }
}
