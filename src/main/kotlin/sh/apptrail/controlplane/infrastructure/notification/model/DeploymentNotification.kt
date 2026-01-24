package sh.apptrail.controlplane.infrastructure.notification.model

import sh.apptrail.controlplane.infrastructure.notification.NotificationType
import java.time.Instant

data class DeploymentNotification(
  val type: NotificationType,
  val workloadId: Long,
  val workloadName: String,
  val workloadKind: String,
  val team: String?,
  val environment: String,
  val cluster: String,
  val namespace: String,
  val currentVersion: String,
  val previousVersion: String?,
  val occurredAt: Instant,
  val deploymentDurationSeconds: Int? = null,
  val errorMessage: String? = null,
)
