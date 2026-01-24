package sh.apptrail.controlplane.application.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.apptrail.controlplane.infrastructure.config.ClusterDefinition
import sh.apptrail.controlplane.infrastructure.config.ClusterEnvironmentProperties
import sh.apptrail.controlplane.infrastructure.config.EnvironmentDefinition
import sh.apptrail.controlplane.infrastructure.config.NamespaceConfig
import sh.apptrail.controlplane.infrastructure.config.ShardConfig

class ClusterTopologyResolverTests {

  private lateinit var resolver: ClusterTopologyResolver

  private val testProperties = ClusterEnvironmentProperties(
    definitions = mapOf(
      "staging-gke01.stg01" to ClusterDefinition(
        environment = "staging",
        alias = "Staging US East 1",
        shard = ShardConfig(name = "stg01", order = 1),
        namespaces = mapOf(
          "special-ns" to NamespaceConfig(
            shard = ShardConfig(name = "stg01-special", order = 2)
          )
        )
      ),
      "staging-gke01.stg02" to ClusterDefinition(
        environment = "staging",
        alias = "Staging US East 2",
        shard = ShardConfig(name = "stg02", order = 3)
      ),
      "production-gke01.prd01" to ClusterDefinition(
        environment = "production",
        alias = "Production US East 1",
        shard = ShardConfig(name = "prd01", order = 10)
      ),
      "production-gke01.prd02" to ClusterDefinition(
        environment = "production",
        shard = ShardConfig(name = "prd02", order = 20)
      ),
      "dev-cluster" to ClusterDefinition(
        environment = "development"
      )
    ),
    environments = listOf(
      EnvironmentDefinition(name = "development", order = 0),
      EnvironmentDefinition(name = "staging", order = 1),
      EnvironmentDefinition(name = "production", order = 2)
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
      val result = resolver.resolveEnvironment("staging-gke01.stg01")
      assertEquals("staging", result)
    }

    @Test
    fun `returns production for production cluster`() {
      val result = resolver.resolveEnvironment("production-gke01.prd01")
      assertEquals("production", result)
    }

    @Test
    fun `returns development for dev cluster`() {
      val result = resolver.resolveEnvironment("dev-cluster")
      assertEquals("development", result)
    }

    @Test
    fun `returns unknown for unconfigured cluster`() {
      val result = resolver.resolveEnvironment("nonexistent-cluster")
      assertEquals("unknown", result)
    }
  }

  @Nested
  inner class ResolveShardTests {

    @Test
    fun `returns correct shard for staging cluster stg01`() {
      val result = resolver.resolveShard("staging-gke01.stg01", "default")

      assertNotNull(result)
      assertEquals("stg01", result!!.name)
      assertEquals(1, result.order)
    }

    @Test
    fun `returns correct shard for staging cluster stg02`() {
      val result = resolver.resolveShard("staging-gke01.stg02", "default")

      assertNotNull(result)
      assertEquals("stg02", result!!.name)
      assertEquals(3, result.order)
    }

    @Test
    fun `returns correct shard for production cluster prd01`() {
      val result = resolver.resolveShard("production-gke01.prd01", "default")

      assertNotNull(result)
      assertEquals("prd01", result!!.name)
      assertEquals(10, result.order)
    }

    @Test
    fun `returns correct shard for production cluster prd02`() {
      val result = resolver.resolveShard("production-gke01.prd02", "default")

      assertNotNull(result)
      assertEquals("prd02", result!!.name)
      assertEquals(20, result.order)
    }

    @Test
    fun `returns null for cluster without shard configuration`() {
      val result = resolver.resolveShard("dev-cluster", "default")
      assertNull(result)
    }

    @Test
    fun `returns null for unconfigured cluster`() {
      val result = resolver.resolveShard("nonexistent-cluster", "default")
      assertNull(result)
    }

    @Test
    fun `returns namespace-level shard override when configured`() {
      val result = resolver.resolveShard("staging-gke01.stg01", "special-ns")

      assertNotNull(result)
      assertEquals("stg01-special", result!!.name)
      assertEquals(2, result.order)
    }

    @Test
    fun `falls back to cluster-level shard for namespace without override`() {
      val result = resolver.resolveShard("staging-gke01.stg01", "other-ns")

      assertNotNull(result)
      assertEquals("stg01", result!!.name)
      assertEquals(1, result.order)
    }
  }

  @Nested
  inner class ResolveAliasTests {

    @Test
    fun `returns alias when configured`() {
      val result = resolver.resolveAlias("staging-gke01.stg01")
      assertEquals("Staging US East 1", result)
    }

    @Test
    fun `returns alias for production cluster`() {
      val result = resolver.resolveAlias("production-gke01.prd01")
      assertEquals("Production US East 1", result)
    }

    @Test
    fun `returns null when alias not configured`() {
      val result = resolver.resolveAlias("production-gke01.prd02")
      assertNull(result)
    }

    @Test
    fun `returns null for unconfigured cluster`() {
      val result = resolver.resolveAlias("nonexistent-cluster")
      assertNull(result)
    }
  }

  @Nested
  inner class GetShardOrderTests {

    @Test
    fun `returns correct order for known shard stg01`() {
      val result = resolver.getShardOrder("stg01")
      assertEquals(1, result)
    }

    @Test
    fun `returns correct order for known shard prd01`() {
      val result = resolver.getShardOrder("prd01")
      assertEquals(10, result)
    }

    @Test
    fun `returns correct order for known shard prd02`() {
      val result = resolver.getShardOrder("prd02")
      assertEquals(20, result)
    }

    @Test
    fun `returns null for unknown shard`() {
      val result = resolver.getShardOrder("nonexistent-shard")
      assertNull(result)
    }
  }

  @Nested
  inner class GetShardsByEnvironmentTests {

    @Test
    fun `groups shards by environment correctly`() {
      val result = resolver.getShardsByEnvironment()

      assertTrue(result.containsKey("staging"))
      assertTrue(result.containsKey("production"))
      assertFalse(result.containsKey("development"))
    }

    @Test
    fun `returns correct shards for staging environment`() {
      val result = resolver.getShardsByEnvironment()
      val stagingShards = result["staging"]!!

      assertEquals(2, stagingShards.size)
      assertEquals("stg01", stagingShards[0].name)
      assertEquals("stg02", stagingShards[1].name)
    }

    @Test
    fun `returns correct shards for production environment`() {
      val result = resolver.getShardsByEnvironment()
      val productionShards = result["production"]!!

      assertEquals(2, productionShards.size)
      assertEquals("prd01", productionShards[0].name)
      assertEquals("prd02", productionShards[1].name)
    }

    @Test
    fun `sorts shards by order within environment`() {
      val result = resolver.getShardsByEnvironment()
      val stagingShards = result["staging"]!!

      assertTrue(stagingShards[0].order < stagingShards[1].order)
    }

    @Test
    fun `deduplicates shards by name`() {
      val propertiesWithDuplicates = ClusterEnvironmentProperties(
        definitions = mapOf(
          "cluster1" to ClusterDefinition(
            environment = "staging",
            shard = ShardConfig(name = "shared-shard", order = 1)
          ),
          "cluster2" to ClusterDefinition(
            environment = "staging",
            shard = ShardConfig(name = "shared-shard", order = 1)
          )
        ),
        environments = emptyList()
      )
      val resolverWithDuplicates = ClusterTopologyResolver(propertiesWithDuplicates)

      val result = resolverWithDuplicates.getShardsByEnvironment()
      val stagingShards = result["staging"]!!

      assertEquals(1, stagingShards.size)
      assertEquals("shared-shard", stagingShards[0].name)
    }
  }

  @Nested
  inner class GetAllShardsTests {

    @Test
    fun `returns all configured shards`() {
      val result = resolver.getAllShards()

      assertEquals(4, result.size)
    }

    @Test
    fun `returns shards sorted by order`() {
      val result = resolver.getAllShards()

      assertEquals("stg01", result[0].name)
      assertEquals(1, result[0].order)
      assertEquals("stg02", result[1].name)
      assertEquals(3, result[1].order)
      assertEquals("prd01", result[2].name)
      assertEquals(10, result[2].order)
      assertEquals("prd02", result[3].name)
      assertEquals(20, result[3].order)
    }

    @Test
    fun `deduplicates shards by name`() {
      val propertiesWithDuplicates = ClusterEnvironmentProperties(
        definitions = mapOf(
          "cluster1" to ClusterDefinition(
            environment = "staging",
            shard = ShardConfig(name = "shared-shard", order = 1)
          ),
          "cluster2" to ClusterDefinition(
            environment = "production",
            shard = ShardConfig(name = "shared-shard", order = 1)
          )
        ),
        environments = emptyList()
      )
      val resolverWithDuplicates = ClusterTopologyResolver(propertiesWithDuplicates)

      val result = resolverWithDuplicates.getAllShards()

      assertEquals(1, result.size)
      assertEquals("shared-shard", result[0].name)
    }

    @Test
    fun `returns empty list when no shards configured`() {
      val propertiesWithoutShards = ClusterEnvironmentProperties(
        definitions = mapOf(
          "cluster1" to ClusterDefinition(environment = "staging")
        ),
        environments = emptyList()
      )
      val resolverWithoutShards = ClusterTopologyResolver(propertiesWithoutShards)

      val result = resolverWithoutShards.getAllShards()

      assertTrue(result.isEmpty())
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
      val propertiesWithoutEnvironments = ClusterEnvironmentProperties(
        definitions = emptyMap(),
        environments = emptyList()
      )
      val resolverWithoutEnvironments = ClusterTopologyResolver(propertiesWithoutEnvironments)

      val result = resolverWithoutEnvironments.getEnvironments()

      assertTrue(result.isEmpty())
    }
  }
}
