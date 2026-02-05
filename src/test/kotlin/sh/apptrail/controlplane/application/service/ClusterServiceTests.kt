package sh.apptrail.controlplane.application.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import sh.apptrail.controlplane.infrastructure.persistence.entity.ClusterEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterRepository

class ClusterServiceTests {

  private lateinit var clusterRepository: ClusterRepository
  private lateinit var clusterService: ClusterService

  @BeforeEach
  fun setUp() {
    clusterRepository = mock()
    clusterService = ClusterService(clusterRepository)
  }

  @Nested
  inner class FindOrCreateClusterTests {

    @Test
    fun `returns existing cluster when found`() {
      val existingCluster = ClusterEntity().apply {
        id = 1L
        name = "production.us-east-1"
      }
      whenever(clusterRepository.findByName("production.us-east-1"))
        .thenReturn(existingCluster)

      val result = clusterService.findOrCreateCluster("production.us-east-1")

      assertEquals(existingCluster, result)
      verify(clusterRepository).findByName("production.us-east-1")
      verify(clusterRepository, never()).save(any())
    }

    @Test
    fun `creates new cluster when not found`() {
      whenever(clusterRepository.findByName("staging.eu-west-1"))
        .thenReturn(null)
      whenever(clusterRepository.save(any())).thenAnswer { invocation ->
        val entity = invocation.getArgument<ClusterEntity>(0)
        entity.apply { id = 2L }
      }

      val result = clusterService.findOrCreateCluster("staging.eu-west-1")

      assertEquals("staging.eu-west-1", result.name)
      assertEquals(2L, result.id)
      verify(clusterRepository).findByName("staging.eu-west-1")
      verify(clusterRepository).save(argThat { name == "staging.eu-west-1" })
    }
  }

  @Nested
  inner class FindClusterTests {

    @Test
    fun `returns cluster when found`() {
      val existingCluster = ClusterEntity().apply {
        id = 1L
        name = "production.us-east-1"
      }
      whenever(clusterRepository.findByName("production.us-east-1"))
        .thenReturn(existingCluster)

      val result = clusterService.findCluster("production.us-east-1")

      assertEquals(existingCluster, result)
      verify(clusterRepository).findByName("production.us-east-1")
    }

    @Test
    fun `returns null when not found`() {
      whenever(clusterRepository.findByName("nonexistent-cluster"))
        .thenReturn(null)

      val result = clusterService.findCluster("nonexistent-cluster")

      assertNull(result)
      verify(clusterRepository).findByName("nonexistent-cluster")
    }
  }
}
