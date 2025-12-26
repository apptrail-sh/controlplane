package sh.apptrail.controlplane.web.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadInstanceRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadRepository
import java.time.Instant

@RestController
@RequestMapping("/api/v1/workloads")
class WorkloadController(
  private val workloadRepository: WorkloadRepository,
  private val workloadInstanceRepository: WorkloadInstanceRepository,
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
