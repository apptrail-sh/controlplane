package sh.apptrail.controlplane.application.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.apptrail.controlplane.infrastructure.config.CellDefinition
import sh.apptrail.controlplane.infrastructure.config.ClusterConfig
import sh.apptrail.controlplane.infrastructure.config.ClusterTopologyProperties
import sh.apptrail.controlplane.infrastructure.config.EnvironmentConfig
import sh.apptrail.controlplane.infrastructure.config.NamespaceConfig

class ClusterTopologyResolverTests {

  private lateinit var resolver: ClusterTopologyResolver

  private val testProperties = ClusterTopologyProperties(
    definitions = mapOf(
      "staging-gke01.stg01" to ClusterConfig(
        environment = "staging",
        alias = "Staging US East 1",
        cell = "stg01",
        namespaces = mapOf(
          "special-ns" to NamespaceConfig(
            cell = "stg01-special"
          )
        )
      ),
      "staging-gke01.stg02" to ClusterConfig(
        environment = "staging",
        alias = "Staging US East 2",
        cell = "stg02"
      ),
      "production-gke01.prd01" to ClusterConfig(
        environment = "production",
        alias = "Production US East 1",
        cell = "prd01"
      ),
      "production-gke01.prd02" to ClusterConfig(
        environment = "production",
        cell = "prd02"
      ),
      "dev-cluster" to ClusterConfig(
        environment = "development"
      ),
      // Multi-tenant cluster hosting multiple environments
      "shared-cluster" to ClusterConfig(
        environment = "development",
        namespaces = mapOf(
          "staging-ns" to NamespaceConfig(
            environment = "staging"
          ),
          "production-ns" to NamespaceConfig(
            environment = "production",
            cell = "shard01"
          )
        )
      )
    ),
    environments = listOf(
      EnvironmentConfig(
        name = "development",
        order = 0
      ),
      EnvironmentConfig(
        name = "staging",
        order = 1,
        cells = listOf(
          CellDefinition(name = "stg01", order = 1, alias = "stg.stg01"),
          CellDefinition(name = "stg01-special", order = 2, alias = "stg.stg01-special"),
          CellDefinition(name = "stg02", order = 3, alias = "stg.stg02")
        )
      ),
      EnvironmentConfig(
        name = "production",
        order = 2,
        cells = listOf(
          CellDefinition(name = "shard01", order = 1, alias = "prd.shard01"),
          CellDefinition(name = "prd01", order = 10, alias = "prd.prd01"),
          CellDefinition(name = "prd02", order = 20)
        )
      )
    )
  )

  @BeforeEach
  fun setUp() {
    resolver = ClusterTopologyResolver(testProperties)
  }

  @Nested
  inner class ResolveEnvironmentTests {

    @Test
    fun `returns staging for staging cluster`() {
      val result = resolver.resolveEnvironment("staging-gke01.stg01", "default")
      assertEquals("staging", result)
    }

    @Test
    fun `returns production for production cluster`() {
      val result = resolver.resolveEnvironment("production-gke01.prd01", "default")
      assertEquals("production", result)
    }

    @Test
    fun `returns development for dev cluster`() {
      val result = resolver.resolveEnvironment("dev-cluster", "default")
      assertEquals("development", result)
    }

    @Test
    fun `returns unknown for unconfigured cluster`() {
      val result = resolver.resolveEnvironment("nonexistent-cluster", "default")
      assertEquals("unknown", result)
    }

    @Test
    fun `returns namespace-level environment override when configured`() {
      val result = resolver.resolveEnvironment("shared-cluster", "staging-ns")
      assertEquals("staging", result)
    }

    @Test
    fun `returns different environment for different namespace in same cluster`() {
      val stagingResult = resolver.resolveEnvironment("shared-cluster", "staging-ns")
      val productionResult = resolver.resolveEnvironment("shared-cluster", "production-ns")

      assertEquals("staging", stagingResult)
      assertEquals("production", productionResult)
    }

    @Test
    fun `falls back to cluster-level environment for namespace without override`() {
      val result = resolver.resolveEnvironment("shared-cluster", "other-ns")
      assertEquals("development", result)
    }

    @Test
    fun `multi-tenant cluster with environment and cell per namespace`() {
      val environment = resolver.resolveEnvironment("shared-cluster", "production-ns")
      val cell = resolver.resolveCell("shared-cluster", "production-ns")

      assertEquals("production", environment)
      assertNotNull(cell)
      assertEquals("shard01", cell!!.name)
      assertEquals(1, cell.order)
    }
  }

  @Nested
  inner class ResolveCellTests {

    @Test
    fun `returns correct cell for staging cluster stg01`() {
      val result = resolver.resolveCell("staging-gke01.stg01", "default")

      assertNotNull(result)
      assertEquals("stg01", result!!.name)
      assertEquals(1, result.order)
      assertEquals("stg.stg01", result.alias)
    }

    @Test
    fun `returns correct cell for staging cluster stg02`() {
      val result = resolver.resolveCell("staging-gke01.stg02", "default")

      assertNotNull(result)
      assertEquals("stg02", result!!.name)
      assertEquals(3, result.order)
      assertEquals("stg.stg02", result.alias)
    }

    @Test
    fun `returns correct cell for production cluster prd01`() {
      val result = resolver.resolveCell("production-gke01.prd01", "default")

      assertNotNull(result)
      assertEquals("prd01", result!!.name)
      assertEquals(10, result.order)
      assertEquals("prd.prd01", result.alias)
    }

    @Test
    fun `returns correct cell for production cluster prd02`() {
      val result = resolver.resolveCell("production-gke01.prd02", "default")

      assertNotNull(result)
      assertEquals("prd02", result!!.name)
      assertEquals(20, result.order)
      assertNull(result.alias)
    }

    @Test
    fun `returns null for cluster without cell configuration`() {
      val result = resolver.resolveCell("dev-cluster", "default")
      assertNull(result)
    }

    @Test
    fun `returns null for unconfigured cluster`() {
      val result = resolver.resolveCell("nonexistent-cluster", "default")
      assertNull(result)
    }

    @Test
    fun `returns namespace-level cell override when configured`() {
      val result = resolver.resolveCell("staging-gke01.stg01", "special-ns")

      assertNotNull(result)
      assertEquals("stg01-special", result!!.name)
      assertEquals(2, result.order)
      assertEquals("stg.stg01-special", result.alias)
    }

    @Test
    fun `falls back to cluster-level cell for namespace without override`() {
      val result = resolver.resolveCell("staging-gke01.stg01", "other-ns")

      assertNotNull(result)
      assertEquals("stg01", result!!.name)
      assertEquals(1, result.order)
      assertEquals("stg.stg01", result.alias)
    }

    @Test
    fun `returns cell with default order when cell is not defined in environment`() {
      val propertiesWithUndefinedCell = ClusterTopologyProperties(
        definitions = mapOf(
          "test-cluster" to ClusterConfig(
            environment = "staging",
            cell = "undefined-cell"
          )
        ),
        environments = listOf(
          EnvironmentConfig(name = "staging", order = 1)
        )
      )
      val resolverWithUndefinedCell = ClusterTopologyResolver(propertiesWithUndefinedCell)

      val result = resolverWithUndefinedCell.resolveCell("test-cluster", "default")

      assertNotNull(result)
      assertEquals("undefined-cell", result!!.name)
      assertEquals(0, result.order)
      assertNull(result.alias)
    }
  }

  @Nested
  inner class ResolveClusterAliasTests {

    @Test
    fun `returns alias when configured`() {
      val result = resolver.resolveClusterAlias("staging-gke01.stg01")
      assertEquals("Staging US East 1", result)
    }

    @Test
    fun `returns alias for production cluster`() {
      val result = resolver.resolveClusterAlias("production-gke01.prd01")
      assertEquals("Production US East 1", result)
    }

    @Test
    fun `returns null when alias not configured`() {
      val result = resolver.resolveClusterAlias("production-gke01.prd02")
      assertNull(result)
    }

    @Test
    fun `returns null for unconfigured cluster`() {
      val result = resolver.resolveClusterAlias("nonexistent-cluster")
      assertNull(result)
    }
  }

  @Nested
  inner class ResolveLocationAliasTests {

    @Test
    fun `returns cell alias when cell has alias`() {
      val result = resolver.resolveLocationAlias("staging-gke01.stg01", "default")
      assertEquals("stg.stg01", result)
    }

    @Test
    fun `returns cluster alias when cell has no alias`() {
      val result = resolver.resolveLocationAlias("production-gke01.prd02", "default")
      assertNull(result)  // prd02 has no alias and cluster has no alias
    }

    @Test
    fun `returns cluster alias when no cell configured`() {
      val result = resolver.resolveLocationAlias("dev-cluster", "default")
      assertNull(result)  // dev-cluster has no cell and no alias
    }

    @Test
    fun `falls back to cluster alias when cell has no alias`() {
      val propertiesWithClusterAlias = ClusterTopologyProperties(
        definitions = mapOf(
          "test-cluster" to ClusterConfig(
            environment = "production",
            alias = "Cluster Alias",
            cell = "cell-without-alias"
          )
        ),
        environments = listOf(
          EnvironmentConfig(
            name = "production",
            order = 2,
            cells = listOf(CellDefinition(name = "cell-without-alias", order = 1))
          )
        )
      )
      val resolverWithClusterAlias = ClusterTopologyResolver(propertiesWithClusterAlias)

      val result = resolverWithClusterAlias.resolveLocationAlias("test-cluster", "default")
      assertEquals("Cluster Alias", result)
    }

    @Test
    fun `prefers cell alias over cluster alias`() {
      val propertiesWithBothAliases = ClusterTopologyProperties(
        definitions = mapOf(
          "test-cluster" to ClusterConfig(
            environment = "production",
            alias = "Cluster Alias",
            cell = "cell-with-alias"
          )
        ),
        environments = listOf(
          EnvironmentConfig(
            name = "production",
            order = 2,
            cells = listOf(CellDefinition(name = "cell-with-alias", order = 1, alias = "Cell Alias"))
          )
        )
      )
      val resolverWithBothAliases = ClusterTopologyResolver(propertiesWithBothAliases)

      val result = resolverWithBothAliases.resolveLocationAlias("test-cluster", "default")
      assertEquals("Cell Alias", result)
    }

    @Test
    fun `returns null for unconfigured cluster`() {
      val result = resolver.resolveLocationAlias("nonexistent-cluster", "default")
      assertNull(result)
    }

    @Test
    fun `uses namespace-level cell for alias resolution`() {
      val result = resolver.resolveLocationAlias("staging-gke01.stg01", "special-ns")
      assertEquals("stg.stg01-special", result)
    }

    @Test
    fun `uses namespace-level cell from multi-tenant cluster`() {
      val result = resolver.resolveLocationAlias("shared-cluster", "production-ns")
      assertEquals("prd.shard01", result)
    }
  }

  @Nested
  inner class GetCellOrderTests {

    @Test
    fun `returns correct order for known cell stg01`() {
      val result = resolver.getCellOrder("stg01")
      assertEquals(1, result)
    }

    @Test
    fun `returns correct order for known cell prd01`() {
      val result = resolver.getCellOrder("prd01")
      assertEquals(10, result)
    }

    @Test
    fun `returns correct order for known cell prd02`() {
      val result = resolver.getCellOrder("prd02")
      assertEquals(20, result)
    }

    @Test
    fun `returns null for unknown cell`() {
      val result = resolver.getCellOrder("nonexistent-cell")
      assertNull(result)
    }
  }

  @Nested
  inner class GetCellsByEnvironmentTests {

    @Test
    fun `groups cells by environment correctly`() {
      val result = resolver.getCellsByEnvironment()

      assertTrue(result.containsKey("staging"))
      assertTrue(result.containsKey("production"))
      assertFalse(result.containsKey("development"))
    }

    @Test
    fun `returns correct cells for staging environment`() {
      val result = resolver.getCellsByEnvironment()
      val stagingCells = result["staging"]!!

      assertEquals(3, stagingCells.size)
      assertEquals("stg01", stagingCells[0].name)
      assertEquals("stg.stg01", stagingCells[0].alias)
      assertEquals("stg01-special", stagingCells[1].name)
      assertEquals("stg02", stagingCells[2].name)
    }

    @Test
    fun `returns correct cells for production environment`() {
      val result = resolver.getCellsByEnvironment()
      val productionCells = result["production"]!!

      assertEquals(3, productionCells.size)
      assertEquals("shard01", productionCells[0].name)
      assertEquals("prd01", productionCells[1].name)
      assertEquals("prd02", productionCells[2].name)
    }

    @Test
    fun `sorts cells by order within environment`() {
      val result = resolver.getCellsByEnvironment()
      val stagingCells = result["staging"]!!

      assertTrue(stagingCells[0].order < stagingCells[1].order)
      assertTrue(stagingCells[1].order < stagingCells[2].order)
    }

    @Test
    fun `returns empty map when no cells configured in environments`() {
      val propertiesWithoutCells = ClusterTopologyProperties(
        definitions = mapOf(
          "cluster1" to ClusterConfig(
            environment = "staging",
            cell = "some-cell"
          )
        ),
        environments = listOf(
          EnvironmentConfig(name = "staging", order = 1)
        )
      )
      val resolverWithoutCells = ClusterTopologyResolver(propertiesWithoutCells)

      val result = resolverWithoutCells.getCellsByEnvironment()

      assertTrue(result.isEmpty())
    }
  }

  @Nested
  inner class GetAllCellsTests {

    @Test
    fun `returns all configured cells`() {
      val result = resolver.getAllCells()

      assertEquals(6, result.size)
    }

    @Test
    fun `returns cells sorted by order`() {
      val result = resolver.getAllCells()

      assertEquals("stg01", result[0].name)
      assertEquals(1, result[0].order)
      assertEquals("shard01", result[1].name)
      assertEquals(1, result[1].order)
      assertEquals("stg01-special", result[2].name)
      assertEquals(2, result[2].order)
      assertEquals("stg02", result[3].name)
      assertEquals(3, result[3].order)
      assertEquals("prd01", result[4].name)
      assertEquals(10, result[4].order)
      assertEquals("prd02", result[5].name)
      assertEquals(20, result[5].order)
    }

    @Test
    fun `deduplicates cells by name`() {
      val propertiesWithDuplicates = ClusterTopologyProperties(
        definitions = emptyMap(),
        environments = listOf(
          EnvironmentConfig(
            name = "staging",
            order = 1,
            cells = listOf(CellDefinition(name = "shared-cell", order = 1))
          ),
          EnvironmentConfig(
            name = "production",
            order = 2,
            cells = listOf(CellDefinition(name = "shared-cell", order = 1))
          )
        )
      )
      val resolverWithDuplicates = ClusterTopologyResolver(propertiesWithDuplicates)

      val result = resolverWithDuplicates.getAllCells()

      assertEquals(1, result.size)
      assertEquals("shared-cell", result[0].name)
    }

    @Test
    fun `returns empty list when no cells configured`() {
      val propertiesWithoutCells = ClusterTopologyProperties(
        definitions = mapOf(
          "cluster1" to ClusterConfig(environment = "staging")
        ),
        environments = listOf(
          EnvironmentConfig(name = "staging", order = 1)
        )
      )
      val resolverWithoutCells = ClusterTopologyResolver(propertiesWithoutCells)

      val result = resolverWithoutCells.getAllCells()

      assertTrue(result.isEmpty())
    }

    @Test
    fun `includes alias in cell info`() {
      val result = resolver.getAllCells()
      val stg01 = result.find { it.name == "stg01" }

      assertNotNull(stg01)
      assertEquals("stg.stg01", stg01!!.alias)
    }
  }

  @Nested
  inner class GetEnvironmentsTests {

    @Test
    fun `returns all configured environments`() {
      val result = resolver.getEnvironments()

      assertEquals(3, result.size)
    }

    @Test
    fun `returns environments sorted by order`() {
      val result = resolver.getEnvironments()

      assertEquals("development", result[0].name)
      assertEquals(0, result[0].order)
      assertEquals("staging", result[1].name)
      assertEquals(1, result[1].order)
      assertEquals("production", result[2].name)
      assertEquals(2, result[2].order)
    }

    @Test
    fun `returns empty list when no environments configured`() {
      val propertiesWithoutEnvironments = ClusterTopologyProperties(
        definitions = emptyMap(),
        environments = emptyList()
      )
      val resolverWithoutEnvironments = ClusterTopologyResolver(propertiesWithoutEnvironments)

      val result = resolverWithoutEnvironments.getEnvironments()

      assertTrue(result.isEmpty())
    }
  }
}
