package sh.apptrail.controlplane.application.service.cleanup

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sh.apptrail.controlplane.infrastructure.config.CleanupProperties
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterHeartbeatRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.NodeRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.PodRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@EnableConfigurationProperties(CleanupProperties::class)
class HardDeleteCleanupService(
  private val nodeRepository: NodeRepository,
  private val podRepository: PodRepository,
  private val heartbeatRepository: ClusterHeartbeatRepository,
  private val cleanupProperties: CleanupProperties
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "\${app.cleanup.hard-delete-cron:0 0 3 * * ?}")
  @Transactional
  fun hardDeleteOldRecords() {
    logger.info("Starting hard delete cleanup")

    val resourceThreshold = Instant.now().minus(
      cleanupProperties.hardDeleteRetentionDays,
      ChronoUnit.DAYS
    )

    val heartbeatThreshold = Instant.now().minus(
      cleanupProperties.heartbeatRetentionDays,
      ChronoUnit.DAYS
    )

    // Hard delete old soft-deleted nodes
    val deletedNodes = nodeRepository.hardDeleteOlderThan(resourceThreshold)
    logger.info("Hard deleted {} nodes older than {} days",
      deletedNodes, cleanupProperties.hardDeleteRetentionDays)

    // Hard delete old soft-deleted pods
    val deletedPods = podRepository.hardDeleteOlderThan(resourceThreshold)
    logger.info("Hard deleted {} pods older than {} days",
      deletedPods, cleanupProperties.hardDeleteRetentionDays)

    // Clean up old heartbeat history
    val deletedHeartbeats = heartbeatRepository.deleteOlderThan(heartbeatThreshold)
    logger.info("Deleted {} heartbeat records older than {} days",
      deletedHeartbeats, cleanupProperties.heartbeatRetentionDays)

    logger.info("Hard delete cleanup completed: {} nodes, {} pods, {} heartbeats removed",
      deletedNodes, deletedPods, deletedHeartbeats)
  }
}
