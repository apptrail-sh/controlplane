package sh.apptrail.controlplane.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "apptrail.cleanup")
data class CleanupProperties(
  val clusterStatusCheckIntervalMs: Long = 300_000, // 5 minutes
  val clusterOfflineThresholdMinutes: Long = 15,
  val staleCleanupCron: String = "0 0 4 * * ?", // Daily at 4 AM
  val staleClusterOfflineDays: Long = 7,
  val hardDeleteCron: String = "0 0 3 * * ?", // Daily at 3 AM
  val hardDeleteRetentionDays: Long = 30,
  val heartbeatRetentionDays: Long = 7
)
