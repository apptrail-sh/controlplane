package sh.apptrail.controlplane.application.service.cleanup

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sh.apptrail.controlplane.application.model.heartbeat.ClusterHeartbeatPayload
import sh.apptrail.controlplane.infrastructure.persistence.entity.ClusterHeartbeatEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.ClusterStatus
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterHeartbeatRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.NodeRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.PodRepository
import java.time.Instant

@Service
class ClusterHeartbeatService(
  private val clusterRepository: ClusterRepository,
  private val heartbeatRepository: ClusterHeartbeatRepository,
  private val nodeRepository: NodeRepository,
  private val podRepository: PodRepository
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Transactional
  fun processHeartbeat(payload: ClusterHeartbeatPayload) {
    val clusterId = payload.source.clusterId
    logger.info("Processing heartbeat from cluster: {} with {} nodes and {} pods",
      clusterId, payload.inventory.nodeUids.size, payload.inventory.podUids.size)

    val cluster = clusterRepository.findByName(clusterId)
    if (cluster == null) {
      logger.warn("Received heartbeat from unknown cluster: {}", clusterId)
      return
    }

    val now = Instant.now()

    // Update cluster heartbeat timestamp and status
    cluster.lastHeartbeatAt = now
    if (cluster.status != ClusterStatus.ONLINE) {
      logger.info("Cluster {} is back online", clusterId)
      cluster.status = ClusterStatus.ONLINE
    }
    clusterRepository.save(cluster)

    // Store heartbeat history
    val heartbeat = ClusterHeartbeatEntity().apply {
      this.cluster = cluster
      this.agentVersion = payload.source.agentVersion
      this.receivedAt = now
      this.nodeCount = payload.inventory.nodeUids.size
      this.podCount = payload.inventory.podUids.size
    }
    heartbeatRepository.save(heartbeat)

    // Reconcile resources: soft-delete any nodes/pods not in the inventory
    reconcileResources(cluster.id!!, payload.inventory.nodeUids, payload.inventory.podUids, now)
  }

  private fun reconcileResources(
    clusterId: Long,
    activeNodeUids: List<String>,
    activePodUids: List<String>,
    deletedAt: Instant
  ) {
    // Reconcile nodes
    if (activeNodeUids.isNotEmpty()) {
      val deletedNodeCount = nodeRepository.softDeleteNotInUidSet(
        clusterId,
        activeNodeUids.toSet(),
        deletedAt
      )
      if (deletedNodeCount > 0) {
        logger.info("Soft-deleted {} stale nodes for cluster {}", deletedNodeCount, clusterId)
      }
    }

    // Reconcile pods
    if (activePodUids.isNotEmpty()) {
      val deletedPodCount = podRepository.softDeleteNotInUidSet(
        clusterId,
        activePodUids.toSet(),
        deletedAt
      )
      if (deletedPodCount > 0) {
        logger.info("Soft-deleted {} stale pods for cluster {}", deletedPodCount, clusterId)
      }
    }
  }
}
