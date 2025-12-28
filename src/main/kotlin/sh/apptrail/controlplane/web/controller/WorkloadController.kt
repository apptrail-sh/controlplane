package sh.apptrail.controlplane.web.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadInstanceRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionHistoryRepository
import java.time.Instant

@RestController
@RequestMapping("/api/v1/workloads")
class WorkloadController(
  private val workloadRepository: WorkloadRepository,
  private val workloadInstanceRepository: WorkloadInstanceRepository,
  private val versionHistoryRepository: VersionHistoryRepository,
) {
  @GetMapping
  fun listWorkloads(): List<WorkloadResponse> {
    val workloads = workloadRepository.findAll()
    if (workloads.isEmpty()) {
      return emptyList()
    }

    val instances = workloadInstanceRepository.findByWorkloadIn(workloads)
    val instancesByWorkloadId = instances.groupBy { it.workload.id }

    return workloads.map { workload ->
      val workloadInstances = instancesByWorkloadId[workload.id].orEmpty()
      WorkloadResponse(
        id = workload.id ?: 0,
        group = workload.group ?: "",
        kind = workload.kind ?: "",
        name = workload.name ?: "",
        team = workload.team,
        createdAt = workload.createdAt,
        updatedAt = workload.updatedAt,
        instances = workloadInstances.map { instance ->
          WorkloadInstanceResponse(
            id = instance.id ?: 0,
            workloadId = workload.id ?: 0,
            clusterId = instance.cluster.id ?: 0,
            cluster = ClusterResponse(
              id = instance.cluster.id ?: 0,
              name = instance.cluster.name,
            ),
            namespace = instance.namespace,
            environment = instance.environment,
            currentVersion = instance.currentVersion,
            labels = instance.labels,
            firstSeenAt = instance.firstSeenAt,
            lastUpdatedAt = instance.lastUpdatedAt,
            createdAt = instance.createdAt,
            updatedAt = instance.updatedAt,
          )
        }
      )
    }
  }

  @GetMapping("/{id}")
  fun getWorkload(@PathVariable id: Long): ResponseEntity<WorkloadResponse> {
    val workload = workloadRepository.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
    val instances = workloadInstanceRepository.findByWorkloadIn(listOf(workload))

    return ResponseEntity.ok(
      WorkloadResponse(
        id = workload.id ?: 0,
        group = workload.group ?: "",
        kind = workload.kind ?: "",
        name = workload.name ?: "",
        team = workload.team,
        createdAt = workload.createdAt,
        updatedAt = workload.updatedAt,
        instances = instances.map { instance ->
          WorkloadInstanceResponse(
            id = instance.id ?: 0,
            workloadId = workload.id ?: 0,
            clusterId = instance.cluster.id ?: 0,
            cluster = ClusterResponse(
              id = instance.cluster.id ?: 0,
              name = instance.cluster.name,
            ),
            namespace = instance.namespace,
            environment = instance.environment,
            currentVersion = instance.currentVersion,
            labels = instance.labels,
            firstSeenAt = instance.firstSeenAt,
            lastUpdatedAt = instance.lastUpdatedAt,
            createdAt = instance.createdAt,
            updatedAt = instance.updatedAt,
          )
        }
      )
    )
  }

  @GetMapping("/{id}/history")
  fun getWorkloadHistory(@PathVariable id: Long): ResponseEntity<List<VersionHistoryResponse>> {
    val instance = workloadInstanceRepository.findById(id).orElse(null)
      ?: return ResponseEntity.notFound().build()

    val history = versionHistoryRepository.findByWorkloadInstance_IdOrderByDetectedAtDesc(id)
    return ResponseEntity.ok(
      history.map { entry ->
        VersionHistoryResponse(
          id = entry.id ?: 0,
          workloadInstanceId = instance.id ?: id,
          previousVersion = entry.previousVersion,
          currentVersion = entry.currentVersion,
          eventType = "deployment",
          deploymentDurationSeconds = entry.deploymentDurationSeconds,
          deploymentStatus = entry.deploymentStatus,
          deploymentPhase = entry.deploymentPhase,
          deploymentStartedAt = entry.deploymentStartedAt,
          deploymentCompletedAt = entry.deploymentCompletedAt,
          deploymentFailedAt = entry.deploymentFailedAt,
          detectedAt = entry.detectedAt,
          createdAt = entry.createdAt,
        )
      }
    )
  }
}

data class WorkloadResponse(
  val id: Long,
  val group: String,
  val kind: String,
  val name: String,
  val team: String?,
  val createdAt: Instant?,
  val updatedAt: Instant?,
  val instances: List<WorkloadInstanceResponse>,
)

data class WorkloadInstanceResponse(
  val id: Long,
  val workloadId: Long,
  val clusterId: Long,
  val cluster: ClusterResponse,
  val namespace: String,
  val environment: String,
  val currentVersion: String?,
  val labels: Map<String, String>?,
  val firstSeenAt: Instant?,
  val lastUpdatedAt: Instant?,
  val createdAt: Instant?,
  val updatedAt: Instant?,
)

data class ClusterResponse(
  val id: Long,
  val name: String,
)

data class VersionHistoryResponse(
  val id: Long,
  val workloadInstanceId: Long,
  val previousVersion: String?,
  val currentVersion: String,
  val eventType: String,
  val deploymentDurationSeconds: Int?,
  val deploymentStatus: String?,
  val deploymentPhase: String?,
  val deploymentStartedAt: Instant?,
  val deploymentCompletedAt: Instant?,
  val deploymentFailedAt: Instant?,
  val detectedAt: Instant,
  val createdAt: Instant?,
)
