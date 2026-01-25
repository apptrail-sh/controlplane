package sh.apptrail.controlplane.web.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.application.service.AlertService
import sh.apptrail.controlplane.application.service.AlertsResult
import sh.apptrail.controlplane.application.service.ClusterTopologyResolver
import sh.apptrail.controlplane.application.service.InstanceKey
import sh.apptrail.controlplane.application.service.MetricsFilters
import sh.apptrail.controlplane.application.service.TeamScorecardResponse
import sh.apptrail.controlplane.application.service.TeamScorecardService
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadInstanceEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadInstanceRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RestController
@RequestMapping("/api/v1/teams")
class TeamController(
  private val workloadRepository: WorkloadRepository,
  private val workloadInstanceRepository: WorkloadInstanceRepository,
  private val clusterTopologyResolver: ClusterTopologyResolver,
  private val teamScorecardService: TeamScorecardService,
  private val alertService: AlertService,
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

    val instanceKeys = instances.map { instance ->
      InstanceKey(
        workloadName = instance.workload.name ?: "",
        workloadKind = instance.workload.kind ?: "",
        clusterName = instance.cluster.name,
        namespace = instance.namespace,
      )
    }
    val alertsByInstance = alertService.getAlertsForInstances(instanceKeys)

    val workloadResponses = workloads.map { workload ->
      workloadToResponse(workload, instancesByWorkloadId[workload.id].orEmpty(), clusterTopologyResolver, alertsByInstance)
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

  @GetMapping("/{name}/scorecard")
  fun getTeamScorecard(
    @PathVariable name: String,
    @RequestParam(required = false) startDate: String?,
    @RequestParam(required = false) endDate: String?,
    @RequestParam(required = false) environment: String?,
    @RequestParam(required = false) clusterId: Long?,
    @RequestParam(required = false, defaultValue = "day") granularity: String
  ): ResponseEntity<TeamScorecardResponse> {
    val parsedStartDate = startDate?.let { parseDate(it) }
      ?: LocalDate.now().minusDays(30).atStartOfDay().toInstant(ZoneOffset.UTC)
    val parsedEndDate = endDate?.let { parseDate(it) }
      ?: Instant.now()

    val filters = MetricsFilters(
      startDate = parsedStartDate,
      endDate = parsedEndDate,
      environment = environment,
      clusterId = clusterId,
      granularity = granularity
    )

    val scorecard = teamScorecardService.getTeamScorecard(name, filters)
      ?: return ResponseEntity.notFound().build()

    return ResponseEntity.ok(scorecard)
  }

  private fun parseDate(dateStr: String): Instant {
    return if (dateStr.contains("T")) {
      Instant.parse(dateStr)
    } else {
      LocalDate.parse(dateStr).atStartOfDay().toInstant(ZoneOffset.UTC)
    }
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
  instances: List<WorkloadInstanceEntity>,
  clusterTopologyResolver: ClusterTopologyResolver,
  alertsByInstance: Map<InstanceKey, AlertsResult>,
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
      val instanceKey = InstanceKey(
        workloadName = workload.name ?: "",
        workloadKind = workload.kind ?: "",
        clusterName = instance.cluster.name,
        namespace = instance.namespace,
      )
      val alerts = alertsByInstance[instanceKey]
      WorkloadInstanceResponse(
        id = instance.id ?: 0,
        workloadId = workload.id ?: 0,
        clusterId = instance.cluster.id ?: 0,
        cluster = ClusterResponse(
          id = instance.cluster.id ?: 0,
          name = instance.cluster.name,
          alias = clusterTopologyResolver.resolveLocationAlias(instance.cluster.name, instance.namespace),
        ),
        namespace = instance.namespace,
        environment = instance.environment,
        cell = instance.cell,
        currentVersion = instance.currentVersion,
        labels = instance.labels,
        firstSeenAt = instance.firstSeenAt,
        lastUpdatedAt = instance.lastUpdatedAt,
        createdAt = instance.createdAt,
        updatedAt = instance.updatedAt,
        alerts = alerts?.toResponse(),
      )
    }
  )
}
