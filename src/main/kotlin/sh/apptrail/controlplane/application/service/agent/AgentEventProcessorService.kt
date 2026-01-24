package sh.apptrail.controlplane.application.service.agent

import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.application.model.agent.AgentEvent
import sh.apptrail.controlplane.application.model.agent.AgentEventOutcome
import sh.apptrail.controlplane.application.model.agent.DeploymentPhase
import sh.apptrail.controlplane.application.service.ClusterTopologyResolver
import sh.apptrail.controlplane.application.service.ReleaseFetchService
import sh.apptrail.controlplane.infrastructure.notification.NotificationEventPublisher
import sh.apptrail.controlplane.infrastructure.notification.NotificationType
import sh.apptrail.controlplane.infrastructure.notification.model.DeploymentNotification
import sh.apptrail.controlplane.infrastructure.persistence.entity.ClusterEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadInstanceEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionHistoryRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadInstanceRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadRepository
import java.time.Instant

@Service
class AgentEventProcessorService(
  private val clusterRepository: ClusterRepository,
  private val workloadRepository: WorkloadRepository,
  private val workloadInstanceRepository: WorkloadInstanceRepository,
  private val versionHistoryRepository: VersionHistoryRepository,
  @Value("\${app.ingest.team-label:team}")
  private val teamLabelKey: String,
  private val releaseFetchService: ReleaseFetchService?,
  private val clusterTopologyResolver: ClusterTopologyResolver,
  private val notificationEventPublisher: NotificationEventPublisher?,
) {

  @Transactional
  fun processEvent(eventPayload: AgentEvent) {
    validateEvent(eventPayload)

    val cluster = clusterRepository.findByName(eventPayload.source.clusterId)
      ?: clusterRepository.save(ClusterEntity().apply {
        name = eventPayload.source.clusterId
      })

    val workloadKind = eventPayload.workload.kind.name
    val workloadName = eventPayload.workload.name
    val workloadPartOf = eventPayload.labels["app.kubernetes.io/part-of"]

    val workloadTeam = workloadTeamFromLabels(eventPayload.labels)

    val existingWorkload = workloadRepository.findByKindAndName(
      kind = workloadKind,
      name = workloadName,
    )

    val workload = if (existingWorkload != null) {
      // Update mutable properties if they changed
      val partOfChanged = existingWorkload.partOf != workloadPartOf
      val teamChanged = existingWorkload.team != workloadTeam

      if (partOfChanged || teamChanged) {
        existingWorkload.partOf = workloadPartOf
        existingWorkload.team = workloadTeam
        workloadRepository.save(existingWorkload)
      } else {
        existingWorkload
      }
    } else {
      workloadRepository.save(WorkloadEntity().apply {
        kind = workloadKind
        name = workloadName
        team = workloadTeam
        partOf = workloadPartOf
      })
    }

    val namespace = eventPayload.workload.namespace
    val clusterId = eventPayload.source.clusterId
    val shardInfo = clusterTopologyResolver.resolveShard(clusterId, namespace)

    val workloadInstance = workloadInstanceRepository.findByWorkloadAndClusterAndNamespace(
      workload = workload,
      cluster = cluster,
      namespace = namespace,
    ) ?: WorkloadInstanceEntity().apply {
      this.workload = workload
      this.cluster = cluster
      this.namespace = namespace
      this.environment = eventPayload.environment
      this.shard = shardInfo?.name
    }

    // Update environment if it changed (e.g., from "unknown" to actual value)
    if (workloadInstance.environment != eventPayload.environment) {
      workloadInstance.environment = eventPayload.environment
    }

    // Only update shard if a valid config is found - don't reset to null if no config matches
    if (shardInfo != null && workloadInstance.shard != shardInfo.name) {
      workloadInstance.shard = shardInfo.name
    }

    val now = Instant.now()
    val isNewInstance = workloadInstance.firstSeenAt == null
    if (isNewInstance) {
      workloadInstance.firstSeenAt = now
      workloadInstance.lastUpdatedAt = now
    }
    workloadInstance.currentVersion = eventPayload.revision?.current
    workloadInstance.labels = eventPayload.labels
    workloadInstanceRepository.save(workloadInstance)

    val revision = eventPayload.revision
    if (revision != null && revision.current.isNotBlank()) {
      val currentVersion = revision.current.trim()
      var previousVersion = revision.previous?.ifBlank { null }
      if (previousVersion == currentVersion) {
        previousVersion = null
      }

      val phaseValue = eventPayload.phase?.name?.lowercase()
      val statusValue = when (eventPayload.outcome) {
        AgentEventOutcome.SUCCEEDED -> "success"
        AgentEventOutcome.FAILED -> "failed"
        null -> null
      }
      val detectedAt = eventPayload.occurredAt

      val latest = versionHistoryRepository.findTopByWorkloadInstance_IdOrderByDetectedAtDesc(workloadInstance.id!!)
      if (latest != null && latest.currentVersion == currentVersion) {
        // Same version - only update phase/status, NOT lastUpdatedAt
        val phaseChanged = phaseValue != null && phaseValue != latest.deploymentPhase
        val statusChanged = statusValue != null && statusValue != latest.deploymentStatus
        if (!phaseChanged && !statusChanged) {
          return
        }

        if (phaseValue != null) {
          latest.deploymentPhase = phaseValue
          when (eventPayload.phase) {
            DeploymentPhase.PENDING -> {}
            DeploymentPhase.PROGRESSING -> {
              if (latest.deploymentStartedAt == null) {
                latest.deploymentStartedAt = detectedAt
              }
            }
            DeploymentPhase.COMPLETED -> {
              latest.deploymentCompletedAt = detectedAt
              // Calculate duration if we have both timestamps
              if (latest.deploymentStartedAt != null) {
                latest.deploymentDurationSeconds = java.time.Duration.between(
                  latest.deploymentStartedAt,
                  detectedAt
                ).seconds.toInt()
              }
            }
            DeploymentPhase.FAILED -> {
              latest.deploymentFailedAt = detectedAt
              // Calculate duration if we have start time (time until failure)
              if (latest.deploymentStartedAt != null && latest.deploymentDurationSeconds == null) {
                latest.deploymentDurationSeconds = java.time.Duration.between(
                  latest.deploymentStartedAt,
                  detectedAt
                ).seconds.toInt()
              }
            }
            null -> {}
          }
        }
        if (statusValue != null) {
          latest.deploymentStatus = statusValue
        } else if (eventPayload.phase == DeploymentPhase.PROGRESSING && latest.deploymentStatus != null) {
          // Clear stale status when transitioning back to progressing (e.g., pod restart after success)
          latest.deploymentStatus = null
        }
        latest.detectedAt = detectedAt
        versionHistoryRepository.save(latest)

        // Publish notification for status/phase updates
        publishNotificationIfNeeded(
          eventPayload = eventPayload,
          workload = workload,
          workloadInstance = workloadInstance,
          currentVersion = currentVersion,
          previousVersion = previousVersion,
          durationSeconds = latest.deploymentDurationSeconds,
        )
        return
      }

      if (latest != null && previousVersion == null) {
        previousVersion = latest.currentVersion
        if (previousVersion == currentVersion) {
          previousVersion = null
        }
      }

      // New version detected - update lastUpdatedAt
      workloadInstance.lastUpdatedAt = now
      workloadInstanceRepository.save(workloadInstance)

      val savedVersionHistory = versionHistoryRepository.save(
        VersionHistoryEntity(
          workloadInstance = workloadInstance,
          previousVersion = previousVersion,
          currentVersion = currentVersion,
          deploymentStatus = statusValue,
          deploymentPhase = phaseValue,
          deploymentStartedAt = when (eventPayload.phase) {
            DeploymentPhase.PROGRESSING -> detectedAt
            else -> null
          },
          deploymentCompletedAt = when (eventPayload.phase) {
            DeploymentPhase.COMPLETED -> detectedAt
            else -> null
          },
          deploymentFailedAt = when (eventPayload.phase) {
            DeploymentPhase.FAILED -> detectedAt
            else -> null
          },
          detectedAt = detectedAt,
        )
      )

      // Queue release fetch for the new version
      releaseFetchService?.queueReleaseFetch(savedVersionHistory.id!!)

      // Publish notification for new version
      publishNotificationIfNeeded(
        eventPayload = eventPayload,
        workload = workload,
        workloadInstance = workloadInstance,
        currentVersion = currentVersion,
        previousVersion = previousVersion,
        durationSeconds = savedVersionHistory.deploymentDurationSeconds,
      )
    }
  }

  private fun validateEvent(eventPayload: AgentEvent) {
    require(eventPayload.eventId.isNotBlank()) { "eventId is required" }
    require(eventPayload.source.clusterId.isNotBlank()) { "source.clusterId is required" }
    require(eventPayload.environment.isNotBlank()) { "environment is required" }
    require(eventPayload.workload.name.isNotBlank()) { "workload.name is required" }
    require(eventPayload.workload.namespace.isNotBlank()) { "workload.namespace is required" }
    eventPayload.revision?.let {
      require(it.current.isNotBlank()) { "revision.current is required when revision is set" }
    }
  }

  private fun workloadTeamFromLabels(labels: Map<String, String>): String? {
    return labels[teamLabelKey]
  }

  private fun publishNotificationIfNeeded(
    eventPayload: AgentEvent,
    workload: WorkloadEntity,
    workloadInstance: WorkloadInstanceEntity,
    currentVersion: String,
    previousVersion: String?,
    durationSeconds: Int?,
  ) {
    if (notificationEventPublisher == null) {
      return
    }

    val notificationType = determineNotificationType(eventPayload) ?: return

    val notification = DeploymentNotification(
      type = notificationType,
      workloadId = workload.id!!,
      workloadName = workload.name ?: "",
      workloadKind = workload.kind ?: "",
      team = workload.team,
      environment = workloadInstance.environment,
      cluster = workloadInstance.cluster.name,
      namespace = workloadInstance.namespace,
      currentVersion = currentVersion,
      previousVersion = previousVersion,
      occurredAt = eventPayload.occurredAt,
      deploymentDurationSeconds = durationSeconds,
      errorMessage = eventPayload.error?.message,
    )

    notificationEventPublisher.publish(notification)
  }

  private fun determineNotificationType(eventPayload: AgentEvent): NotificationType? {
    return when {
      eventPayload.phase == DeploymentPhase.FAILED -> NotificationType.DEPLOYMENT_FAILED
      eventPayload.outcome == AgentEventOutcome.FAILED -> NotificationType.DEPLOYMENT_FAILED
      eventPayload.phase == DeploymentPhase.COMPLETED && eventPayload.outcome == AgentEventOutcome.SUCCEEDED ->
        NotificationType.DEPLOYMENT_SUCCEEDED
      eventPayload.phase == DeploymentPhase.PROGRESSING -> NotificationType.DEPLOYMENT_STARTED
      else -> null
    }
  }
}
