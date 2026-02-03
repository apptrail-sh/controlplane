package sh.apptrail.controlplane.application.service.cleanup

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sh.apptrail.controlplane.infrastructure.config.CleanupProperties
import sh.apptrail.controlplane.infrastructure.persistence.entity.ClusterStatus
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@EnableConfigurationProperties(CleanupProperties::class)
class ClusterStatusService(
  private val clusterRepository: ClusterRepository,
  private val cleanupProperties: CleanupProperties
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Scheduled(fixedDelayString = "\${app.cleanup.cluster-status-check-interval-ms:300000}")
  @Transactional
  fun checkClusterStatus() {
    logger.debug("Checking cluster status for offline detection")

    val threshold = Instant.now().minus(
      cleanupProperties.clusterOfflineThresholdMinutes,
      ChronoUnit.MINUTES
    )

    // Find online clusters with no recent heartbeat
    val staleClusters = clusterRepository.findOnlineClustersWithNoRecentHeartbeat(threshold)

    if (staleClusters.isEmpty()) {
      logger.debug("No clusters need status update")
      return
    }

    val clusterIds = staleClusters.mapNotNull { it.id }
    val clusterNames = staleClusters.map { it.name }

    logger.info("Marking {} clusters as OFFLINE: {}", clusterIds.size, clusterNames)

    clusterRepository.updateStatusByIds(clusterIds, ClusterStatus.OFFLINE)

    logger.info("Marked {} clusters as OFFLINE", clusterIds.size)
  }
}
