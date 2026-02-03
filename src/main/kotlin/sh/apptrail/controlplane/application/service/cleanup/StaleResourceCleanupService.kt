package sh.apptrail.controlplane.application.service.cleanup

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sh.apptrail.controlplane.infrastructure.config.CleanupProperties
import sh.apptrail.controlplane.infrastructure.persistence.entity.ClusterStatus
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.NodeRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.PodRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@EnableConfigurationProperties(CleanupProperties::class)
class StaleResourceCleanupService(
  private val clusterRepository: ClusterRepository,
  private val nodeRepository: NodeRepository,
  private val podRepository: PodRepository,
  private val cleanupProperties: CleanupProperties
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "\${app.cleanup.stale-cleanup-cron:0 0 4 * * ?}")
  @Transactional
  fun cleanupStaleResources() {
    logger.info("Starting stale resource cleanup for long-term offline clusters")

    val threshold = Instant.now().minus(
      cleanupProperties.staleClusterOfflineDays,
      ChronoUnit.DAYS
    )

    // Find clusters that have been offline for longer than the threshold
    val staleClusters = clusterRepository.findByStatusAndLastHeartbeatAtBefore(
      ClusterStatus.OFFLINE,
      threshold
    )

    if (staleClusters.isEmpty()) {
      logger.info("No stale clusters found (offline > {} days)", cleanupProperties.staleClusterOfflineDays)
      return
    }

    logger.info("Found {} clusters offline for more than {} days",
      staleClusters.size, cleanupProperties.staleClusterOfflineDays)

    val now = Instant.now()
    var totalNodes = 0
    var totalPods = 0

    for (cluster in staleClusters) {
      val clusterId = cluster.id ?: continue

      logger.info("Cleaning up resources for stale cluster: {} (last heartbeat: {})",
        cluster.name, cluster.lastHeartbeatAt)

      // Mark all nodes as deleted
      val deletedNodes = nodeRepository.markAllAsDeletedForCluster(clusterId, now)
      totalNodes += deletedNodes

      // Mark all pods as deleted
      val deletedPods = podRepository.markAllAsDeletedForCluster(clusterId, now)
      totalPods += deletedPods

      logger.info("Marked {} nodes and {} pods as deleted for cluster {}",
        deletedNodes, deletedPods, cluster.name)
    }

    logger.info("Stale resource cleanup completed: {} nodes and {} pods marked as deleted across {} clusters",
      totalNodes, totalPods, staleClusters.size)
  }
}
