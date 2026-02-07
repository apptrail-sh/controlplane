package sh.apptrail.controlplane.application.service.cleanup

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import sh.apptrail.controlplane.application.model.heartbeat.ClusterHeartbeatPayload
import sh.apptrail.controlplane.application.model.heartbeat.ResourceInventory
import sh.apptrail.controlplane.application.model.heartbeat.SourceMetadata
import sh.apptrail.controlplane.infrastructure.persistence.entity.ClusterEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.ClusterStatus
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterHeartbeatRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.NodeRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.PodRepository
import java.time.Instant

class ClusterHeartbeatServiceTests {

  private lateinit var clusterRepository: ClusterRepository
  private lateinit var heartbeatRepository: ClusterHeartbeatRepository
  private lateinit var nodeRepository: NodeRepository
  private lateinit var podRepository: PodRepository
  private lateinit var service: ClusterHeartbeatService

  @BeforeEach
  fun setUp() {
    clusterRepository = mock()
    heartbeatRepository = mock()
    nodeRepository = mock()
    podRepository = mock()
    service = ClusterHeartbeatService(clusterRepository, heartbeatRepository, nodeRepository, podRepository)
  }

  private fun createPayload(
    clusterId: String = "test-cluster",
    occurredAt: Instant = Instant.now(),
    nodeUids: List<String> = emptyList(),
    podUids: List<String> = emptyList()
  ) = ClusterHeartbeatPayload(
    eventId = "test-event",
    occurredAt = occurredAt,
    source = SourceMetadata(clusterId = clusterId, agentVersion = "1.0.0"),
    inventory = ResourceInventory(nodeUids = nodeUids, podUids = podUids)
  )

  private fun createCluster(id: Long = 1L, name: String = "test-cluster") = ClusterEntity().apply {
    this.id = id
    this.name = name
  }

  @Nested
  inner class ProcessHeartbeat {

    @Test
    fun `ignores heartbeat from unknown cluster`() {
      whenever(clusterRepository.findByName("unknown-cluster")).thenReturn(null)

      service.processHeartbeat(createPayload(clusterId = "unknown-cluster"))

      verify(podRepository, never()).softDeleteNotInUidSet(any(), any(), any(), any())
      verify(nodeRepository, never()).softDeleteNotInUidSet(any(), any(), any(), any())
    }

    @Test
    fun `soft-deletes pods not in heartbeat inventory`() {
      val cluster = createCluster()
      whenever(clusterRepository.findByName("test-cluster")).thenReturn(cluster)
      whenever(clusterRepository.save(any())).thenReturn(cluster)
      whenever(heartbeatRepository.save(any())).thenAnswer { it.arguments[0] }
      whenever(podRepository.softDeleteNotInUidSet(any(), any(), any(), any())).thenReturn(2)

      val occurredAt = Instant.parse("2025-01-15T10:00:00Z")
      service.processHeartbeat(createPayload(
        occurredAt = occurredAt,
        podUids = listOf("pod-uid-1", "pod-uid-2")
      ))

      verify(podRepository).softDeleteNotInUidSet(
        eq(1L),
        eq(setOf("pod-uid-1", "pod-uid-2")),
        any(),
        eq(occurredAt)
      )
    }

    @Test
    fun `soft-deletes nodes not in heartbeat inventory`() {
      val cluster = createCluster()
      whenever(clusterRepository.findByName("test-cluster")).thenReturn(cluster)
      whenever(clusterRepository.save(any())).thenReturn(cluster)
      whenever(heartbeatRepository.save(any())).thenAnswer { it.arguments[0] }
      whenever(nodeRepository.softDeleteNotInUidSet(any(), any(), any(), any())).thenReturn(1)

      val occurredAt = Instant.parse("2025-01-15T10:00:00Z")
      service.processHeartbeat(createPayload(
        occurredAt = occurredAt,
        nodeUids = listOf("node-uid-1")
      ))

      verify(nodeRepository).softDeleteNotInUidSet(
        eq(1L),
        eq(setOf("node-uid-1")),
        any(),
        eq(occurredAt)
      )
    }

    @Test
    fun `passes heartbeat occurredAt as timestamp guard to prevent race condition`() {
      val cluster = createCluster()
      whenever(clusterRepository.findByName("test-cluster")).thenReturn(cluster)
      whenever(clusterRepository.save(any())).thenReturn(cluster)
      whenever(heartbeatRepository.save(any())).thenAnswer { it.arguments[0] }
      whenever(podRepository.softDeleteNotInUidSet(any(), any(), any(), any())).thenReturn(0)
      whenever(nodeRepository.softDeleteNotInUidSet(any(), any(), any(), any())).thenReturn(0)

      // Simulate a stale heartbeat: occurredAt is in the past
      // Pods created/updated AFTER this timestamp should NOT be soft-deleted
      val staleOccurredAt = Instant.parse("2025-01-15T09:55:00Z")

      service.processHeartbeat(createPayload(
        occurredAt = staleOccurredAt,
        podUids = listOf("old-pod-uid"),
        nodeUids = listOf("old-node-uid")
      ))

      // Verify that heartbeatOccurredAt is passed to the repository
      // The DB query will use this to skip pods/nodes with lastUpdatedAt >= heartbeatOccurredAt
      verify(podRepository).softDeleteNotInUidSet(
        eq(1L),
        eq(setOf("old-pod-uid")),
        any(),
        eq(staleOccurredAt)
      )
      verify(nodeRepository).softDeleteNotInUidSet(
        eq(1L),
        eq(setOf("old-node-uid")),
        any(),
        eq(staleOccurredAt)
      )
    }

    @Test
    fun `skips pod reconciliation when no pod UIDs in inventory`() {
      val cluster = createCluster()
      whenever(clusterRepository.findByName("test-cluster")).thenReturn(cluster)
      whenever(clusterRepository.save(any())).thenReturn(cluster)
      whenever(heartbeatRepository.save(any())).thenAnswer { it.arguments[0] }

      service.processHeartbeat(createPayload(
        podUids = emptyList(),
        nodeUids = listOf("node-uid-1")
      ))

      verify(podRepository, never()).softDeleteNotInUidSet(any(), any(), any(), any())
    }

    @Test
    fun `updates cluster status to ONLINE when offline`() {
      val cluster = createCluster().apply {
        status = ClusterStatus.OFFLINE
      }
      whenever(clusterRepository.findByName("test-cluster")).thenReturn(cluster)
      whenever(clusterRepository.save(any())).thenReturn(cluster)
      whenever(heartbeatRepository.save(any())).thenAnswer { it.arguments[0] }

      service.processHeartbeat(createPayload())

      assertEquals(ClusterStatus.ONLINE, cluster.status)
      assertNotNull(cluster.lastHeartbeatAt)
      verify(clusterRepository).save(cluster)
    }
  }
}
