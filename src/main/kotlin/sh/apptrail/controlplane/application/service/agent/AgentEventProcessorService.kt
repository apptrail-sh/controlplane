package sh.apptrail.controlplane.application.service.agent

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sh.apptrail.controlplane.application.model.agent.AgentEvent
import sh.apptrail.controlplane.application.model.agent.AgentEventOutcome
import sh.apptrail.controlplane.application.model.agent.DeploymentPhase
import sh.apptrail.controlplane.application.service.ReleaseFetchService
import sh.apptrail.controlplane.infrastructure.notification.NotificationEventPublisher
import sh.apptrail.controlplane.infrastructure.notification.NotificationType
import sh.apptrail.controlplane.infrastructure.notification.model.DeploymentNotification
import sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadInstanceEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionHistoryRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadInstanceRepository
import java.time.Duration
import java.time.Instant

@Service
class AgentEventProcessorService(
  private val entityResolver: AgentEventEntityResolver,
  private val workloadInstanceRepository: WorkloadInstanceRepository,
  private val versionHistoryRepository: VersionHistoryRepository,
  private val releaseFetchService: ReleaseFetchService?,
  private val notificationEventPublisher: NotificationEventPublisher?,
) {

  private val log = LoggerFactory.getLogger(AgentEventProcessorService::class.java)

  companion object {
    private const val STATUS_SUCCESS = "success"
    private const val STATUS_FAILED = "failed"
  }

  @Transactional
  fun processEvent(eventPayload: AgentEvent) {
    validateEvent(eventPayload)

    val cluster = entityResolver.resolveCluster(eventPayload.source.clusterId)
    val workload = entityResolver.resolveWorkload(
      kind = eventPayload.workload.kind.name,
      name = eventPayload.workload.name,
      partOf = eventPayload.labels[AgentEventEntityResolver.PART_OF_LABEL],
      team = entityResolver.workloadTeamFromLabels(eventPayload.labels),
    )
    val environment = entityResolver.resolveEnvironment(
      clusterId = eventPayload.source.clusterId,
      namespace = eventPayload.workload.namespace,
      agentProvidedEnvironment = eventPayload.environment,
    )
    val workloadInstance = entityResolver.resolveWorkloadInstance(
      workload = workload,
      cluster = cluster,
      namespace = eventPayload.workload.namespace,
      clusterId = eventPayload.source.clusterId,
      environment = environment,
      eventPayload = eventPayload,
    )

    processVersionHistory(workloadInstance, workload, eventPayload)
  }

  // --- Version History Methods ---

  private fun processVersionHistory(
    workloadInstance: WorkloadInstanceEntity,
    workload: WorkloadEntity,
    eventPayload: AgentEvent,
  ) {
    val revision = eventPayload.revision ?: return
    if (revision.current.isBlank()) return

    val currentVersion = revision.current.trim()
    var previousVersion = normalizePreviousVersion(revision.previous, currentVersion)
    val phaseValue = eventPayload.phase?.name?.lowercase()
    val statusValue = mapOutcomeToStatus(eventPayload.outcome)
    val detectedAt = eventPayload.occurredAt

    val latest = versionHistoryRepository.findTopByWorkloadInstance_IdOrderByDetectedAtDesc(workloadInstance.id!!)

    if (latest != null && latest.currentVersion == currentVersion) {
      val updated = updateExistingVersionHistory(latest, eventPayload, phaseValue, statusValue, detectedAt)
      if (updated) {
        publishNotificationIfNeeded(
          versionHistory = latest,
          eventPayload = eventPayload,
          workload = workload,
          workloadInstance = workloadInstance,
          currentVersion = currentVersion,
          previousVersion = previousVersion,
          durationSeconds = latest.deploymentDurationSeconds,
        )
      }
      return
    }

    // Derive previousVersion from latest if not provided
    if (latest != null && previousVersion == null) {
      previousVersion = latest.currentVersion
      if (previousVersion == currentVersion) {
        previousVersion = null
      }
    }

    createNewVersionHistory(
      workloadInstance = workloadInstance,
      workload = workload,
      eventPayload = eventPayload,
      currentVersion = currentVersion,
      previousVersion = previousVersion,
      phaseValue = phaseValue,
      statusValue = statusValue,
      detectedAt = detectedAt,
    )
  }

  private fun updateExistingVersionHistory(
    latest: VersionHistoryEntity,
    eventPayload: AgentEvent,
    phaseValue: String?,
    statusValue: String?,
    detectedAt: Instant,
  ): Boolean {
    val phaseChanged = phaseValue != null && phaseValue != latest.deploymentPhase
    val statusChanged = statusValue != null && statusValue != latest.deploymentStatus

    if (!phaseChanged && !statusChanged) {
      return false
    }

    if (phaseValue != null) {
      latest.deploymentPhase = phaseValue
      applyPhaseTimestamps(latest, eventPayload.phase, detectedAt)
    }

    if (statusValue != null) {
      latest.deploymentStatus = statusValue
    } else if (eventPayload.phase == DeploymentPhase.PROGRESSING && latest.deploymentStatus != null) {
      // Clear stale status when transitioning back to progressing
      latest.deploymentStatus = null
    }

    latest.detectedAt = detectedAt
    versionHistoryRepository.save(latest)
    return true
  }

  private fun createNewVersionHistory(
    workloadInstance: WorkloadInstanceEntity,
    workload: WorkloadEntity,
    eventPayload: AgentEvent,
    currentVersion: String,
    previousVersion: String?,
    phaseValue: String?,
    statusValue: String?,
    detectedAt: Instant,
  ) {
    val existing = versionHistoryRepository.findByWorkloadInstanceIdAndCurrentVersionAndPreviousVersion(
      workloadInstance.id!!,
      currentVersion,
      previousVersion,
    )

    if (existing != null) {
      log.debug(
        "Version history already exists for workloadInstance={}, version={}, updating existing record",
        workloadInstance.id,
        currentVersion,
      )
      workloadInstance.lastUpdatedAt = Instant.now()
      workloadInstanceRepository.save(workloadInstance)
      updateExistingVersionHistory(existing, eventPayload, phaseValue, statusValue, detectedAt)
      publishNotificationIfNeeded(
        versionHistory = existing,
        eventPayload = eventPayload,
        workload = workload,
        workloadInstance = workloadInstance,
        currentVersion = currentVersion,
        previousVersion = previousVersion,
        durationSeconds = existing.deploymentDurationSeconds,
      )
      return
    }

    // New version detected - update lastUpdatedAt
    workloadInstance.lastUpdatedAt = Instant.now()
    workloadInstanceRepository.save(workloadInstance)

    val versionHistory = VersionHistoryEntity(
      workloadInstance = workloadInstance,
      previousVersion = previousVersion,
      currentVersion = currentVersion,
      deploymentStatus = statusValue,
      deploymentPhase = phaseValue,
      deploymentStartedAt = if (eventPayload.phase == DeploymentPhase.PROGRESSING) detectedAt else null,
      deploymentCompletedAt = if (eventPayload.phase == DeploymentPhase.COMPLETED) detectedAt else null,
      deploymentFailedAt = if (eventPayload.phase == DeploymentPhase.FAILED) detectedAt else null,
      detectedAt = detectedAt,
    )

    val savedVersionHistory = versionHistoryRepository.save(versionHistory)

    releaseFetchService?.queueReleaseFetch(savedVersionHistory.id!!)

    publishNotificationIfNeeded(
      versionHistory = savedVersionHistory,
      eventPayload = eventPayload,
      workload = workload,
      workloadInstance = workloadInstance,
      currentVersion = currentVersion,
      previousVersion = previousVersion,
      durationSeconds = savedVersionHistory.deploymentDurationSeconds,
    )
  }

  private fun applyPhaseTimestamps(
    versionHistory: VersionHistoryEntity,
    phase: DeploymentPhase?,
    detectedAt: Instant,
  ) {
    when (phase) {
      DeploymentPhase.PENDING -> {}
      DeploymentPhase.PROGRESSING -> {
        if (versionHistory.deploymentStartedAt == null) {
          versionHistory.deploymentStartedAt = detectedAt
        }
      }
      DeploymentPhase.COMPLETED -> {
        versionHistory.deploymentCompletedAt = detectedAt
        versionHistory.deploymentDurationSeconds = calculateDeploymentDuration(
          versionHistory.deploymentStartedAt,
          detectedAt,
        )
      }
      DeploymentPhase.FAILED -> {
        versionHistory.deploymentFailedAt = detectedAt
        if (versionHistory.deploymentDurationSeconds == null) {
          versionHistory.deploymentDurationSeconds = calculateDeploymentDuration(
            versionHistory.deploymentStartedAt,
            detectedAt,
          )
        }
      }
      null -> {}
    }
  }

  // --- Simple Helper Methods ---

  private fun calculateDeploymentDuration(startedAt: Instant?, endedAt: Instant): Int? {
    return startedAt?.let { Duration.between(it, endedAt).seconds.toInt() }
  }

  private fun mapOutcomeToStatus(outcome: AgentEventOutcome?): String? {
    return when (outcome) {
      AgentEventOutcome.SUCCEEDED -> STATUS_SUCCESS
      AgentEventOutcome.FAILED -> STATUS_FAILED
      null -> null
    }
  }

  private fun normalizePreviousVersion(previous: String?, current: String): String? {
    val normalized = previous?.ifBlank { null }
    return if (normalized == current) null else normalized
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

  // --- Notification Methods ---

  private fun publishNotificationIfNeeded(
    versionHistory: VersionHistoryEntity,
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

    val phaseValue = eventPayload.phase?.name?.lowercase() ?: return

    // Check if we've already notified for this phase (idempotency)
    if (versionHistory.lastNotifiedPhase == phaseValue) {
      log.debug("Skipping duplicate notification for phase: {}", phaseValue)
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

    // Update lastNotifiedPhase after successful publish
    versionHistory.lastNotifiedPhase = phaseValue
    versionHistoryRepository.save(versionHistory)
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
