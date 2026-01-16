package sh.apptrail.controlplane.application.service.agent

import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.application.model.agent.AgentEvent
import sh.apptrail.controlplane.application.model.agent.AgentEventOutcome
import sh.apptrail.controlplane.application.model.agent.DeploymentPhase
import sh.apptrail.controlplane.infrastructure.persistence.entity.ClusterEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadInstanceEntity
import sh.apptrail.controlplane.application.service.VersionImpactAnalysisService
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
  private val versionImpactAnalysisService: VersionImpactAnalysisService? = null,
) {

  @Transactional
  fun processEvent(eventPayload: AgentEvent) {
    validateEvent(eventPayload)

    val cluster = clusterRepository.findByName(eventPayload.source.clusterId)
      ?: clusterRepository.save(ClusterEntity().apply {
        name = eventPayload.source.clusterId
      })

    val workloadGroup = workloadGroupFromLabels(eventPayload.labels, eventPayload.workload.name)
    val workloadKind = eventPayload.workload.kind.name
    val workloadName = eventPayload.workload.name

    val workload = workloadRepository.findByGroupAndKindAndName(
      group = workloadGroup,
      kind = workloadKind,
      name = workloadName,
    ) ?: workloadRepository.save(WorkloadEntity().apply {
      group = workloadGroup
      kind = workloadKind
      name = workloadName
      team = workloadTeamFromLabels(eventPayload.labels)
    })

    val namespace = eventPayload.workload.namespace
    val workloadInstance = workloadInstanceRepository.findByWorkloadAndClusterAndNamespace(
      workload = workload,
      cluster = cluster,
      namespace = namespace,
    ) ?: WorkloadInstanceEntity().apply {
      this.workload = workload
      this.cluster = cluster
      this.namespace = namespace
      this.environment = eventPayload.environment
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
        }
        latest.detectedAt = detectedAt
        versionHistoryRepository.save(latest)

        if (eventPayload.phase == DeploymentPhase.COMPLETED) {
          versionImpactAnalysisService?.scheduleAnalysis(latest.id!!)
        }
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

      if (eventPayload.phase == DeploymentPhase.COMPLETED) {
        versionImpactAnalysisService?.scheduleAnalysis(savedVersionHistory.id!!)
      }
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

  private fun workloadGroupFromLabels(labels: Map<String, String>, fallback: String): String {
    return labels["app.kubernetes.io/part-of"]
      ?: labels["app.kubernetes.io/name"]
      ?: fallback
  }

  private fun workloadTeamFromLabels(labels: Map<String, String>): String? {
    return labels[teamLabelKey]
  }
}
