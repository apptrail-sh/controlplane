package sh.apptrail.controlplane.web.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadInstanceRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadRepository
import java.time.Instant

@RestController
@RequestMapping("/api/v1/teams")
class TeamController(
  private val workloadRepository: WorkloadRepository,
  private val workloadInstanceRepository: WorkloadInstanceRepository,
) {
  @GetMapping
  fun listTeams(): List<TeamResponse> {
    val workloads = workloadRepository.findAll()
    if (workloads.isEmpty()) {
      return emptyList()
    }

    val instances = workloadInstanceRepository.findByWorkloadIn(workloads)
    val instancesByWorkloadId = instances.groupBy { it.workload.id }

    val grouped = workloads.groupBy { it.team ?: "unassigned" }
    return grouped.map { (teamName, teamWorkloads) ->
      val latestActivity = teamWorkloads.mapNotNull { workload ->
        instancesByWorkloadId[workload.id].orEmpty()
          .mapNotNull { it.lastUpdatedAt }
          .maxOrNull()
      }.maxOrNull()

      TeamResponse(
        name = teamName,
        workloadCount = teamWorkloads.size,
        latestActivity = latestActivity,
      )
    }.sortedBy { it.name }
  }

  @GetMapping("/{name}")
  fun getTeam(@PathVariable name: String): ResponseEntity<TeamDetailResponse> {
    val workloads = if (name == "unassigned") {
      workloadRepository.findAll().filter { it.team.isNullOrBlank() }
    } else {
      workloadRepository.findByTeam(name)
    }

    if (workloads.isEmpty()) {
      return ResponseEntity.notFound().build()
    }

    val instances = workloadInstanceRepository.findByWorkloadIn(workloads)
    val instancesByWorkloadId = instances.groupBy { it.workload.id }

    val workloadResponses = workloads.map { workload ->
      workloadToResponse(workload, instancesByWorkloadId[workload.id].orEmpty())
    }

    val latestActivity = instances.mapNotNull { it.lastUpdatedAt }.maxOrNull()
    return ResponseEntity.ok(
      TeamDetailResponse(
        name = name,
        workloadCount = workloads.size,
        latestActivity = latestActivity,
        workloads = workloadResponses,
      )
    )
  }
}

data class TeamResponse(
  val name: String,
  val workloadCount: Int,
  val latestActivity: Instant?,
)

data class TeamDetailResponse(
  val name: String,
  val workloadCount: Int,
  val latestActivity: Instant?,
  val workloads: List<WorkloadResponse>,
)

private fun workloadToResponse(
  workload: WorkloadEntity,
  instances: List<sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadInstanceEntity>,
): WorkloadResponse {
  return WorkloadResponse(
    id = workload.id ?: 0,
    kind = workload.kind ?: "",
    name = workload.name ?: "",
    team = workload.team,
    partOf = workload.partOf,
    repositoryUrl = workload.repositoryUrl,
    description = workload.description,
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
        shard = instance.shard,
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
