package sh.apptrail.controlplane.web.controller

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.application.service.ClusterEnvironmentResolver
import sh.apptrail.controlplane.application.service.WorkloadService
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadInstanceRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionHistoryRepository
import sh.apptrail.controlplane.web.dto.UpdateWorkloadRequest
import java.time.Instant

@RestController
@RequestMapping("/api/v1/workloads")
class WorkloadController(
  private val workloadRepository: WorkloadRepository,
  private val workloadInstanceRepository: WorkloadInstanceRepository,
  private val versionHistoryRepository: VersionHistoryRepository,
  private val workloadService: WorkloadService,
  private val clusterEnvironmentResolver: ClusterEnvironmentResolver,
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
        kind = workload.kind ?: "",
        name = workload.name ?: "",
        team = workload.team,
        partOf = workload.partOf,
        repositoryUrl = workload.repositoryUrl,
        description = workload.description,
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
              alias = clusterEnvironmentResolver.resolveAlias(instance.cluster.name),
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
  }

  @GetMapping("/{id}")
  fun getWorkload(@PathVariable id: Long): ResponseEntity<WorkloadResponse> {
    val workload = workloadRepository.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
    val instances = workloadInstanceRepository.findByWorkloadIn(listOf(workload))

    return ResponseEntity.ok(
      WorkloadResponse(
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
              alias = clusterEnvironmentResolver.resolveAlias(instance.cluster.name),
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
    )
  }

  @PatchMapping("/{id}")
  fun updateWorkload(
    @PathVariable id: Long,
    @Valid @RequestBody request: UpdateWorkloadRequest,
  ): ResponseEntity<WorkloadResponse> {
    val updatedWorkload = workloadService.updateWorkload(id, request)
      ?: return ResponseEntity.notFound().build()

    val instances = workloadInstanceRepository.findByWorkloadIn(listOf(updatedWorkload))

    return ResponseEntity.ok(
      WorkloadResponse(
        id = updatedWorkload.id ?: 0,
        kind = updatedWorkload.kind ?: "",
        name = updatedWorkload.name ?: "",
        team = updatedWorkload.team,
        partOf = updatedWorkload.partOf,
        repositoryUrl = updatedWorkload.repositoryUrl,
        description = updatedWorkload.description,
        createdAt = updatedWorkload.createdAt,
        updatedAt = updatedWorkload.updatedAt,
        instances = instances.map { instance ->
          WorkloadInstanceResponse(
            id = instance.id ?: 0,
            workloadId = updatedWorkload.id ?: 0,
            clusterId = instance.cluster.id ?: 0,
            cluster = ClusterResponse(
              id = instance.cluster.id ?: 0,
              name = instance.cluster.name,
              alias = clusterEnvironmentResolver.resolveAlias(instance.cluster.name),
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
          releaseFetchStatus = entry.releaseFetchStatus,
          releaseInfo = entry.releaseInfo?.let { info ->
            ReleaseInfoResponse(
              provider = info.provider,
              tagName = info.tagName,
              name = info.name,
              body = info.body,
              publishedAt = info.publishedAt,
              htmlUrl = info.htmlUrl,
              authors = info.authors.map { author ->
                ReleaseAuthorResponse(
                  login = author.login,
                  avatarUrl = author.avatarUrl,
                )
              },
              isDraft = info.isDraft,
              isPrerelease = info.isPrerelease,
            )
          },
        )
      }
    )
  }
}

data class WorkloadResponse(
  val id: Long,
  val kind: String,
  val name: String,
  val team: String?,
  val partOf: String?,
  val repositoryUrl: String?,
  val description: String?,
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
  val shard: String?,
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
  val alias: String?,
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
  val releaseFetchStatus: String?,
  val releaseInfo: ReleaseInfoResponse?,
)

data class ReleaseInfoResponse(
  val provider: String,
  val tagName: String,
  val name: String?,
  val body: String?,
  val publishedAt: Instant?,
  val htmlUrl: String?,
  val authors: List<ReleaseAuthorResponse>,
  val isDraft: Boolean,
  val isPrerelease: Boolean,
)

data class ReleaseAuthorResponse(
  val login: String,
  val avatarUrl: String?,
)
