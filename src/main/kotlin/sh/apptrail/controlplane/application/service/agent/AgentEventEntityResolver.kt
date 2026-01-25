package sh.apptrail.controlplane.application.service.agent

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.application.model.agent.AgentEvent
import sh.apptrail.controlplane.application.service.ClusterTopologyResolver
import sh.apptrail.controlplane.infrastructure.persistence.entity.ClusterEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadInstanceEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadInstanceRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadRepository
import java.time.Instant

@Service
class AgentEventEntityResolver(
  private val clusterRepository: ClusterRepository,
  private val workloadRepository: WorkloadRepository,
  private val workloadInstanceRepository: WorkloadInstanceRepository,
  private val clusterTopologyResolver: ClusterTopologyResolver,
  @Value("\${app.ingest.team-label:team}")
  private val teamLabelKey: String,
) {

  companion object {
    private const val UNKNOWN_ENVIRONMENT = "unknown"
    const val PART_OF_LABEL = "app.kubernetes.io/part-of"
  }

  fun resolveCluster(clusterId: String): ClusterEntity {
    return clusterRepository.findByName(clusterId)
      ?: clusterRepository.save(ClusterEntity().apply { name = clusterId })
  }

  fun resolveWorkload(
    kind: String,
    name: String,
    partOf: String?,
    team: String?,
  ): WorkloadEntity {
    val existing = workloadRepository.findByKindAndName(kind = kind, name = name)

    return if (existing != null) {
      val partOfChanged = existing.partOf != partOf
      val teamChanged = existing.team != team

      if (partOfChanged || teamChanged) {
        existing.partOf = partOf
        existing.team = team
        workloadRepository.save(existing)
      } else {
        existing
      }
    } else {
      workloadRepository.save(WorkloadEntity().apply {
        this.kind = kind
        this.name = name
        this.team = team
        this.partOf = partOf
      })
    }
  }

  fun resolveEnvironment(
    clusterId: String,
    namespace: String,
    agentProvidedEnvironment: String,
  ): String {
    val resolved = clusterTopologyResolver.resolveEnvironment(clusterId, namespace)
    return if (resolved != UNKNOWN_ENVIRONMENT) resolved else agentProvidedEnvironment
  }

  fun resolveWorkloadInstance(
    workload: WorkloadEntity,
    cluster: ClusterEntity,
    namespace: String,
    clusterId: String,
    environment: String,
    eventPayload: AgentEvent,
  ): WorkloadInstanceEntity {
    val cellInfo = clusterTopologyResolver.resolveCell(clusterId, namespace)

    val workloadInstance = workloadInstanceRepository.findByWorkloadAndClusterAndNamespace(
      workload = workload,
      cluster = cluster,
      namespace = namespace,
    ) ?: WorkloadInstanceEntity().apply {
      this.workload = workload
      this.cluster = cluster
      this.namespace = namespace
      this.environment = environment
      this.cell = cellInfo?.name
    }

    // Update environment if it changed
    if (workloadInstance.environment != environment) {
      workloadInstance.environment = environment
    }

    // Only update cell if a valid config is found
    if (cellInfo != null && workloadInstance.cell != cellInfo.name) {
      workloadInstance.cell = cellInfo.name
    }

    val now = Instant.now()
    if (workloadInstance.firstSeenAt == null) {
      workloadInstance.firstSeenAt = now
      workloadInstance.lastUpdatedAt = now
    }
    workloadInstance.currentVersion = eventPayload.revision?.current
    workloadInstance.labels = eventPayload.labels

    return workloadInstanceRepository.save(workloadInstance)
  }

  fun workloadTeamFromLabels(labels: Map<String, String>): String? {
    return labels[teamLabelKey]
  }
}
