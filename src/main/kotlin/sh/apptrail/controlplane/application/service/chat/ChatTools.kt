package sh.apptrail.controlplane.application.service.chat

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Description
import sh.apptrail.controlplane.application.service.*
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadInstanceRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionHistoryRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.function.Function

@Configuration
@ConditionalOnProperty(name = ["apptrail.ai.enabled"], havingValue = "true")
class ChatTools(
  private val analyticsService: AnalyticsService,
  private val teamScorecardService: TeamScorecardService,
  private val workloadRepository: WorkloadRepository,
  private val workloadInstanceRepository: WorkloadInstanceRepository,
  private val versionHistoryRepository: VersionHistoryRepository,
  private val metricsCalculator: MetricsCalculator
) {

  // Tool request/response classes
  @JsonClassDescription("Request to query workloads with optional filters")
  data class QueryWorkloadsRequest(
    @JsonProperty("team")
    @JsonPropertyDescription("Filter by team name")
    val team: String? = null,

    @JsonProperty("name")
    @JsonPropertyDescription("Filter by workload name (partial match)")
    val name: String? = null,

    @JsonProperty("environment")
    @JsonPropertyDescription("Filter by environment (e.g., staging, production)")
    val environment: String? = null,

    @JsonProperty("limit")
    @JsonPropertyDescription("Maximum number of results to return (default 10)")
    val limit: Int = 10
  )

  data class WorkloadInfo(
    val id: Long,
    val name: String,
    val kind: String,
    val team: String?,
    val currentVersion: String?,
    val environment: String?,
    val cluster: String?,
    val lastDeployedAt: String?
  )

  @JsonClassDescription("Request to get team metrics")
  data class GetTeamMetricsRequest(
    @JsonProperty("teamName")
    @JsonPropertyDescription("Name of the team")
    val teamName: String,

    @JsonProperty("daysBack")
    @JsonPropertyDescription("Number of days to look back (default 30)")
    val daysBack: Int = 30
  )

  @JsonClassDescription("Request to get analytics overview")
  data class GetAnalyticsOverviewRequest(
    @JsonProperty("environment")
    @JsonPropertyDescription("Filter by environment")
    val environment: String? = null,

    @JsonProperty("team")
    @JsonPropertyDescription("Filter by team")
    val team: String? = null,

    @JsonProperty("daysBack")
    @JsonPropertyDescription("Number of days to look back (default 30)")
    val daysBack: Int = 30
  )

  @JsonClassDescription("Request to get unstable workloads")
  data class GetUnstableWorkloadsRequest(
    @JsonProperty("hoursBack")
    @JsonPropertyDescription("Number of hours to look back (default 24)")
    val hoursBack: Int = 24,

    @JsonProperty("limit")
    @JsonPropertyDescription("Maximum number of results (default 10)")
    val limit: Int = 10
  )

  data class UnstableWorkload(
    val workloadId: Long,
    val workloadName: String,
    val workloadKind: String,
    val team: String?,
    val failureCount: Int,
    val totalDeployments: Int,
    val failureRate: Double,
    val rollbackCount: Int
  )

  @JsonClassDescription("Request to get workload deployment history")
  data class GetWorkloadHistoryRequest(
    @JsonProperty("workloadName")
    @JsonPropertyDescription("Name of the workload")
    val workloadName: String,

    @JsonProperty("limit")
    @JsonPropertyDescription("Maximum number of history entries (default 10)")
    val limit: Int = 10
  )

  data class DeploymentHistoryEntry(
    val version: String,
    val previousVersion: String?,
    val deploymentPhase: String?,
    val detectedAt: String,
    val environment: String?,
    val cluster: String?
  )

  @JsonClassDescription("Request to compare teams")
  data class CompareTeamsRequest(
    @JsonProperty("daysBack")
    @JsonPropertyDescription("Number of days to look back (default 30)")
    val daysBack: Int = 30,

    @JsonProperty("limit")
    @JsonPropertyDescription("Number of top teams to compare (default 10)")
    val limit: Int = 10
  )

  data class TeamComparison(
    val team: String,
    val rank: Int,
    val overallGrade: String,
    val deploymentFrequency: Double,
    val failureRate: Double,
    val averageLeadTimeSeconds: Double,
    val totalDeployments: Int
  )

  @JsonClassDescription("Request to get slowest deployments")
  data class GetSlowestDeploymentsRequest(
    @JsonProperty("environment")
    @JsonPropertyDescription("Filter by environment")
    val environment: String? = null,

    @JsonProperty("daysBack")
    @JsonPropertyDescription("Number of days to look back (default 30)")
    val daysBack: Int = 30,

    @JsonProperty("limit")
    @JsonPropertyDescription("Maximum number of results (default 10)")
    val limit: Int = 10
  )

  // Tool beans
  @Bean
  @Description("Query workloads with optional filters by team, name, or environment. Returns basic workload information including current version and deployment status.")
  fun queryWorkloads(): Function<QueryWorkloadsRequest, List<WorkloadInfo>> {
    return Function { request ->
      var workloads = workloadRepository.findAll()

      // Apply filters
      if (!request.team.isNullOrBlank()) {
        workloads = workloads.filter { it.team?.contains(request.team, ignoreCase = true) == true }
      }
      if (!request.name.isNullOrBlank()) {
        workloads = workloads.filter { it.name?.contains(request.name, ignoreCase = true) == true }
      }

      val instances = workloadInstanceRepository.findByWorkloadIn(workloads)
      val instancesByWorkloadId = instances.groupBy { it.workload.id }

      // Apply environment filter and build results
      workloads.take(request.limit).flatMap { workload ->
        var workloadInstances = instancesByWorkloadId[workload.id] ?: emptyList()

        if (!request.environment.isNullOrBlank()) {
          workloadInstances = workloadInstances.filter {
            it.environment?.contains(request.environment, ignoreCase = true) == true
          }
        }

        if (workloadInstances.isEmpty()) {
          listOf(WorkloadInfo(
            id = workload.id ?: 0,
            name = workload.name ?: "",
            kind = workload.kind ?: "",
            team = workload.team,
            currentVersion = null,
            environment = null,
            cluster = null,
            lastDeployedAt = null
          ))
        } else {
          workloadInstances.map { instance ->
            WorkloadInfo(
              id = workload.id ?: 0,
              name = workload.name ?: "",
              kind = workload.kind ?: "",
              team = workload.team,
              currentVersion = instance.currentVersion,
              environment = instance.environment,
              cluster = instance.cluster?.name,
              lastDeployedAt = instance.lastUpdatedAt?.toString()
            )
          }
        }
      }
    }
  }

  @Bean
  @Description("Get DORA metrics and performance scorecard for a specific team. Returns deployment frequency, lead time, change failure rate, and MTTR with grades.")
  fun getTeamMetrics(): Function<GetTeamMetricsRequest, TeamScorecardResponse?> {
    return Function { request ->
      val endDate = Instant.now()
      val startDate = endDate.minus(request.daysBack.toLong(), ChronoUnit.DAYS)

      val filters = MetricsFilters(
        startDate = startDate,
        endDate = endDate
      )

      teamScorecardService.getTeamScorecard(request.teamName, filters)
    }
  }

  @Bean
  @Description("Get overall analytics including deployment frequency, lead time, MTTR, and change failure rate. Can be filtered by environment or team.")
  fun getAnalyticsOverview(): Function<GetAnalyticsOverviewRequest, DashboardOverviewResponse> {
    return Function { request ->
      val endDate = LocalDate.now()
      val startDate = endDate.minusDays(request.daysBack.toLong())

      val filters = AnalyticsFilters(
        startDate = startDate.toString(),
        endDate = endDate.toString(),
        environment = request.environment,
        team = request.team
      )

      analyticsService.getOverview(filters)
    }
  }

  @Bean
  @Description("Find the most unstable workloads based on failure rate and rollback frequency. Returns workloads with high failure rates or frequent rollbacks in the specified time period.")
  fun getUnstableWorkloads(): Function<GetUnstableWorkloadsRequest, List<UnstableWorkload>> {
    return Function { request ->
      val endDate = Instant.now()
      val startDate = endDate.minus(request.hoursBack.toLong(), ChronoUnit.HOURS)

      val allHistory = versionHistoryRepository.findByDateRange(startDate, endDate)
      val allWorkloads = workloadRepository.findAll()
      val allInstances = workloadInstanceRepository.findByWorkloadIn(allWorkloads)

      val workloadMap = allWorkloads.associateBy { it.id }
      val instancesByWorkloadId = allInstances.groupBy { it.workload.id }

      // Group history by workload
      val historyByWorkload = allHistory.groupBy { entry ->
        val instance = allInstances.find { it.id == entry.workloadInstance.id }
        instance?.workload?.id
      }

      historyByWorkload.mapNotNull { (workloadId, history) ->
        if (workloadId == null) return@mapNotNull null
        val workload = workloadMap[workloadId] ?: return@mapNotNull null

        val failureCount = history.count { it.deploymentPhase == "failed" }
        val totalDeployments = history.size
        val rollbackCount = history.count {
          it.previousVersion != null && it.currentVersion < (it.previousVersion ?: "")
        }
        val failureRate = if (totalDeployments > 0) (failureCount.toDouble() / totalDeployments) * 100 else 0.0

        UnstableWorkload(
          workloadId = workloadId,
          workloadName = workload.name ?: "",
          workloadKind = workload.kind ?: "",
          team = workload.team,
          failureCount = failureCount,
          totalDeployments = totalDeployments,
          failureRate = failureRate,
          rollbackCount = rollbackCount
        )
      }
        .filter { it.failureCount > 0 || it.rollbackCount > 0 }
        .sortedByDescending { it.failureRate + (it.rollbackCount * 10) }
        .take(request.limit)
    }
  }

  @Bean
  @Description("Get deployment history for a specific workload. Shows recent version changes with deployment status and timing.")
  fun getWorkloadHistory(): Function<GetWorkloadHistoryRequest, List<DeploymentHistoryEntry>> {
    return Function { request ->
      val workload = workloadRepository.findAll()
        .find { it.name?.contains(request.workloadName, ignoreCase = true) == true }
        ?: return@Function emptyList()

      val instances = workloadInstanceRepository.findByWorkloadIn(listOf(workload))

      instances.flatMap { instance ->
        versionHistoryRepository.findByWorkloadInstance_IdOrderByDetectedAtDesc(instance.id!!)
          .take(request.limit)
          .map { history ->
            DeploymentHistoryEntry(
              version = history.currentVersion,
              previousVersion = history.previousVersion,
              deploymentPhase = history.deploymentPhase,
              detectedAt = history.detectedAt.toString(),
              environment = instance.environment,
              cluster = instance.cluster?.name
            )
          }
      }.sortedByDescending { it.detectedAt }.take(request.limit)
    }
  }

  @Bean
  @Description("Compare team performance across DORA metrics. Returns ranked list of teams with their grades and key metrics.")
  fun compareTeams(): Function<CompareTeamsRequest, List<TeamComparison>> {
    return Function { request ->
      val endDate = Instant.now()
      val startDate = endDate.minus(request.daysBack.toLong(), ChronoUnit.DAYS)

      val filters = MetricsFilters(
        startDate = startDate,
        endDate = endDate
      )

      val scorecards = teamScorecardService.getAllTeamScorecards(filters)

      scorecards.teams.take(request.limit).map { team ->
        TeamComparison(
          team = team.team,
          rank = team.rank,
          overallGrade = team.overallGrade.name,
          deploymentFrequency = team.totalDeployments.toDouble() / request.daysBack,
          failureRate = team.failureRate,
          averageLeadTimeSeconds = team.averageLeadTimeSeconds,
          totalDeployments = team.totalDeployments
        )
      }
    }
  }

  @Bean
  @Description("Find the slowest deployments by average duration. Can be filtered by environment.")
  fun getSlowestDeployments(): Function<GetSlowestDeploymentsRequest, List<SlowestDeploymentResponse>> {
    return Function { request ->
      val endDate = LocalDate.now()
      val startDate = endDate.minusDays(request.daysBack.toLong())

      val filters = AnalyticsFilters(
        startDate = startDate.toString(),
        endDate = endDate.toString(),
        environment = request.environment
      )

      val overview = analyticsService.getOverview(filters)
      overview.slowestDeployments.take(request.limit)
    }
  }
}
