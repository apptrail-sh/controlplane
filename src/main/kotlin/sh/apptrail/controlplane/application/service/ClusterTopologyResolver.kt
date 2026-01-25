package sh.apptrail.controlplane.application.service

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.config.CellDefinition
import sh.apptrail.controlplane.infrastructure.config.ClusterTopologyProperties

data class CellInfo(
  val name: String,
  val order: Int,
  val alias: String? = null,
)

@Service
@EnableConfigurationProperties(ClusterTopologyProperties::class)
class ClusterTopologyResolver(
  private val properties: ClusterTopologyProperties,
) {

  /**
   * Resolves the environment for a given cluster and namespace.
   * Resolution order:
   * 1. First check for namespace-level environment override
   * 2. Fall back to cluster-level environment
   * @return Environment name, or "unknown" if cluster is not configured
   */
  fun resolveEnvironment(clusterId: String, namespace: String): String {
    val definition = properties.definitions[clusterId] ?: return "unknown"

    // Check namespace-level override first
    definition.namespaces[namespace]?.environment?.let { return it }

    // Fall back to cluster-level environment
    return definition.environment
  }

  /**
   * Resolves the cell for a given cluster and namespace.
   * Resolution order:
   * 1. First check for namespace-level cell override
   * 2. Fall back to cluster-level cell
   * 3. Look up cell definition from environment config
   * @return CellInfo if a cell is configured, null otherwise
   */
  fun resolveCell(clusterId: String, namespace: String): CellInfo? {
    val definition = properties.definitions[clusterId] ?: return null
    val environment = resolveEnvironment(clusterId, namespace)

    // Check namespace-level override first
    val cellName = definition.namespaces[namespace]?.cell ?: definition.cell ?: return null

    // Look up the cell definition from the environment
    val cellDef = findCellDefinition(environment, cellName)
    return if (cellDef != null) {
      CellInfo(name = cellDef.name, order = cellDef.order, alias = cellDef.alias)
    } else {
      // Cell name is configured but not defined in environment - return with default order
      CellInfo(name = cellName, order = 0)
    }
  }

  /**
   * Resolves the cluster-level alias for a given cluster.
   * Note: For workload instances, prefer resolveLocationAlias() which includes cell alias resolution.
   * @return the cluster alias if configured, null otherwise
   */
  fun resolveClusterAlias(clusterId: String): String? {
    return properties.definitions[clusterId]?.alias
  }

  /**
   * Resolves the location alias for a workload instance.
   * Resolution order:
   * 1. Cell alias (if workload has a cell and the cell has an alias)
   * 2. Cluster alias (fallback)
   * @return the location alias, or null if none configured
   */
  fun resolveLocationAlias(clusterId: String, namespace: String): String? {
    val definition = properties.definitions[clusterId] ?: return null
    val environment = resolveEnvironment(clusterId, namespace)

    // Try cell alias first
    val cellName = definition.namespaces[namespace]?.cell ?: definition.cell
    if (cellName != null) {
      val cellDef = findCellDefinition(environment, cellName)
      cellDef?.alias?.let { return it }
    }

    // Fall back to cluster alias
    return definition.alias
  }

  /**
   * Finds a cell definition by name within an environment.
   * @return CellDefinition if found, null otherwise
   */
  private fun findCellDefinition(environment: String, cellName: String): CellDefinition? {
    return properties.environments
      .find { it.name == environment }
      ?.cells
      ?.find { it.name == cellName }
  }

  /**
   * Gets the order for a cell by name.
   * @return the cell order, or null if cell is not configured
   */
  fun getCellOrder(cellName: String): Int? {
    return properties.environments
      .flatMap { it.cells }
      .find { it.name == cellName }
      ?.order
  }

  /**
   * Gets all configured cells grouped by environment.
   * Cells are read from environment config, not cluster definitions.
   * @return Map of environment name to list of CellInfo
   */
  fun getCellsByEnvironment(): Map<String, List<CellInfo>> {
    return properties.environments
      .filter { it.cells.isNotEmpty() }
      .associate { env ->
        env.name to env.cells
          .map { CellInfo(name = it.name, order = it.order, alias = it.alias) }
          .sortedBy { it.order }
      }
  }

  /**
   * Gets all configured cells as a flat list (deduplicated by name).
   * Cells are read from environment config, not cluster definitions.
   * @return List of CellInfo sorted by order
   */
  fun getAllCells(): List<CellInfo> {
    return properties.environments
      .flatMap { it.cells }
      .map { CellInfo(name = it.name, order = it.order, alias = it.alias) }
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
