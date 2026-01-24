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
class ClusterTopologyResolver(
  private val properties: ClusterEnvironmentProperties,
) {

  /**
   * Resolves the environment for a given cluster.
   */
  fun resolveEnvironment(clusterId: String): String {
    return properties.definitions[clusterId]?.environment ?: "unknown"
  }

  /**
   * Resolves the shard for a given cluster and namespace.
   * Resolution order:
   * 1. First check for namespace-level shard override
   * 2. Fall back to cluster-level shard
   * @return ShardInfo if a shard is configured, null otherwise
   */
  fun resolveShard(clusterId: String, namespace: String): ShardInfo? {
    val definition = properties.definitions[clusterId] ?: return null

    // Check namespace-level override first
    definition.namespaces[namespace]?.shard?.let {
      return ShardInfo(name = it.name, order = it.order)
    }

    // Fall back to cluster-level shard
    return definition.shard?.let { ShardInfo(name = it.name, order = it.order) }
  }

  /**
   * Resolves the alias for a given cluster.
   * @return the alias if configured, null otherwise
   */
  fun resolveAlias(clusterId: String): String? {
    return properties.definitions[clusterId]?.alias
  }

  /**
   * Gets the order for a shard by name.
   * @return the shard order, or null if shard is not configured
   */
  fun getShardOrder(shardName: String): Int? {
    return properties.definitions.values
      .mapNotNull { it.shard }
      .find { it.name == shardName }
      ?.order
  }

  /**
   * Gets all configured shards grouped by environment.
   * @return Map of environment name to list of ShardInfo
   */
  fun getShardsByEnvironment(): Map<String, List<ShardInfo>> {
    val result = mutableMapOf<String, MutableList<ShardInfo>>()

    for ((_, definition) in properties.definitions) {
      val environment = definition.environment
      definition.shard?.let { shard ->
        val shardInfo = ShardInfo(name = shard.name, order = shard.order)
        result.getOrPut(environment) { mutableListOf() }.add(shardInfo)
      }
    }

    return result.mapValues { (_, shards) ->
      shards.distinctBy { it.name }.sortedBy { it.order }
    }
  }

  /**
   * Gets all configured shards as a flat list (deduplicated by name).
   * @return List of ShardInfo sorted by order
   */
  fun getAllShards(): List<ShardInfo> {
    return properties.definitions.values
      .mapNotNull { it.shard }
      .map { ShardInfo(name = it.name, order = it.order) }
      .distinctBy { it.name }
      .sortedBy { it.order }
  }

  /**
   * Gets all configured environments with their order.
   * @return List of environment names sorted by order
   */
  fun getEnvironments(): List<EnvironmentInfo> {
    return properties.environments
      .sortedBy { it.order }
      .map { EnvironmentInfo(name = it.name, order = it.order) }
  }
}

data class EnvironmentInfo(
  val name: String,
  val order: Int,
)
