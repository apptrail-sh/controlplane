package sh.apptrail.controlplane.application.service

import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadInstanceEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionHistoryRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadInstanceRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadRepository

@Service
class TeamScorecardService(
  private val workloadRepository: WorkloadRepository,
  private val workloadInstanceRepository: WorkloadInstanceRepository,
  private val versionHistoryRepository: VersionHistoryRepository,
  private val metricsCalculator: MetricsCalculator
) {

  fun getTeamScorecard(teamName: String, filters: MetricsFilters): TeamScorecardResponse? {
    val workloads = if (teamName == "unassigned") {
      workloadRepository.findAll().filter { it.team.isNullOrBlank() }
    } else {
      workloadRepository.findByTeam(teamName)
    }

    if (workloads.isEmpty()) {
      return null
    }

    val history = if (teamName == "unassigned") {
      versionHistoryRepository.findByUnassignedTeamAndDateRange(
        startDate = filters.startDate,
        endDate = filters.endDate
      )
    } else {
      versionHistoryRepository.findByTeamAndDateRange(
        team = teamName,
        startDate = filters.startDate,
        endDate = filters.endDate
      )
    }

    // Apply optional filters
    var filteredHistory = history
    if (filters.environment != null) {
      filteredHistory = filteredHistory.filter { it.workloadInstance.environment == filters.environment }
    }
    if (filters.clusterId != null) {
      filteredHistory = filteredHistory.filter { it.workloadInstance.cluster.id == filters.clusterId }
    }

    // Calculate DORA metrics
    val deploymentFrequency = metricsCalculator.calculateDeploymentFrequency(
      filteredHistory, filters.startDate, filters.endDate
    )
    val leadTime = metricsCalculator.calculateLeadTime(filteredHistory)
    val changeFailureRate = metricsCalculator.calculateChangeFailureRate(filteredHistory)
    val mttr = metricsCalculator.calculateMTTR(filteredHistory)

    // Calculate grades
    val dfGrade = metricsCalculator.gradeDeploymentFrequency(deploymentFrequency.deploymentsPerDay)
    val ltGrade = metricsCalculator.gradeLeadTime(leadTime.averageSeconds)
    val cfrGrade = metricsCalculator.gradeChangeFailureRate(changeFailureRate.failureRate)
    val mttrGrade = metricsCalculator.gradeMTTR(mttr.averageSeconds)
    val overallGrade = metricsCalculator.calculateOverallGrade(dfGrade, ltGrade, cfrGrade, mttrGrade)

    // Calculate workload breakdown
    val instances = workloadInstanceRepository.findByWorkloadIn(workloads)
    val workloadBreakdown = calculateWorkloadBreakdown(workloads, instances, filteredHistory)

    // Get comparison data - need all teams data
    val comparison = calculateTeamComparison(teamName, filters)

    return TeamScorecardResponse(
      team = teamName,
      performanceGrade = TeamPerformanceGrade(
        overall = overallGrade,
        deploymentFrequency = dfGrade,
        leadTime = ltGrade,
        changeFailureRate = cfrGrade,
        mttr = mttrGrade
      ),
      doraMetrics = TeamDoraMetrics(
        deploymentFrequency = deploymentFrequency,
        leadTime = leadTime,
        changeFailureRate = changeFailureRate,
        mttr = mttr
      ),
      workloadBreakdown = workloadBreakdown,
      comparison = comparison,
      dateRange = DateRange(filters.startDate, filters.endDate)
    )
  }

  fun getAllTeamScorecards(filters: MetricsFilters): TeamScorecardsResponse {
    val allWorkloads = workloadRepository.findAll()
    val allHistory = versionHistoryRepository.findByDateRange(
      startDate = filters.startDate,
      endDate = filters.endDate
    )

    // Apply optional filters
    var filteredHistory = allHistory
    if (filters.environment != null) {
      filteredHistory = filteredHistory.filter { it.workloadInstance.environment == filters.environment }
    }
    if (filters.clusterId != null) {
      filteredHistory = filteredHistory.filter { it.workloadInstance.cluster.id == filters.clusterId }
    }

    // Group workloads and history by team
    val workloadsByTeam = allWorkloads.groupBy { it.team ?: "unassigned" }
    val allInstances = workloadInstanceRepository.findByWorkloadIn(allWorkloads)
    val instancesByWorkloadId = allInstances.groupBy { it.workload.id }

    // Calculate metrics per team
    val teamSummaries = workloadsByTeam.map { (team, workloads) ->
      val workloadIds = workloads.mapNotNull { it.id }.toSet()
      val teamInstanceIds = workloads.flatMap { wl ->
        instancesByWorkloadId[wl.id]?.mapNotNull { it.id } ?: emptyList()
      }.toSet()

      val teamHistory = filteredHistory.filter { it.workloadInstance.id in teamInstanceIds }

      val deploymentFrequency = metricsCalculator.calculateDeploymentFrequency(
        teamHistory, filters.startDate, filters.endDate
      )
      val leadTime = metricsCalculator.calculateLeadTime(teamHistory)
      val changeFailureRate = metricsCalculator.calculateChangeFailureRate(teamHistory)
      val mttr = metricsCalculator.calculateMTTR(teamHistory)

      val dfGrade = metricsCalculator.gradeDeploymentFrequency(deploymentFrequency.deploymentsPerDay)
      val ltGrade = metricsCalculator.gradeLeadTime(leadTime.averageSeconds)
      val cfrGrade = metricsCalculator.gradeChangeFailureRate(changeFailureRate.failureRate)
      val mttrGrade = metricsCalculator.gradeMTTR(mttr.averageSeconds)
      val overallGrade = metricsCalculator.calculateOverallGrade(dfGrade, ltGrade, cfrGrade, mttrGrade)

      TeamScorecardSummary(
        team = team,
        overallGrade = overallGrade,
        deploymentFrequencyGrade = dfGrade,
        leadTimeGrade = ltGrade,
        changeFailureRateGrade = cfrGrade,
        mttrGrade = mttrGrade,
        totalDeployments = deploymentFrequency.totalDeployments,
        failureRate = changeFailureRate.failureRate,
        averageLeadTimeSeconds = leadTime.averageSeconds,
        workloadCount = workloads.size,
        rank = 0 // Will be calculated after sorting
      )
    }

    // Sort by overall grade (best first) then by deployment count
    val sortedSummaries = teamSummaries
      .sortedWith(compareBy<TeamScorecardSummary> { it.overallGrade.ordinal }
        .thenByDescending { it.totalDeployments })
      .mapIndexed { index, summary -> summary.copy(rank = index + 1) }

    // Calculate overall metrics
    val totalDeployments = filteredHistory.size
    val overallFailureRate = if (totalDeployments > 0) {
      (filteredHistory.count { metricsCalculator.isFailed(it) }.toDouble() / totalDeployments) * 100
    } else {
      0.0
    }
    val durations = filteredHistory.mapNotNull { metricsCalculator.getDeploymentDuration(it) }
    val overallAvgLeadTime = if (durations.isNotEmpty()) durations.average() else 0.0

    return TeamScorecardsResponse(
      teams = sortedSummaries,
      dateRange = DateRange(filters.startDate, filters.endDate),
      totalDeployments = totalDeployments,
      overallFailureRate = overallFailureRate,
      overallAverageLeadTimeSeconds = overallAvgLeadTime
    )
  }

  private fun calculateWorkloadBreakdown(
    workloads: List<WorkloadEntity>,
    instances: List<WorkloadInstanceEntity>,
    history: List<VersionHistoryEntity>
  ): List<WorkloadSummaryMetrics> {
    val instancesByWorkloadId = instances.groupBy { it.workload.id }

    return workloads.mapNotNull { workload ->
      val workloadInstanceIds = instancesByWorkloadId[workload.id]?.mapNotNull { it.id }?.toSet()
        ?: return@mapNotNull null

      val workloadHistory = history.filter { it.workloadInstance.id in workloadInstanceIds }

      if (workloadHistory.isEmpty()) {
        return@mapNotNull WorkloadSummaryMetrics(
          workloadId = workload.id ?: 0,
          workloadName = workload.name ?: "",
          workloadKind = workload.kind ?: "",
          deploymentCount = 0,
          failureCount = 0,
          failureRate = 0.0,
          averageLeadTimeSeconds = 0.0,
          performanceGrade = PerformanceGrade.LOW
        )
      }

      val failureCount = workloadHistory.count { metricsCalculator.isFailed(it) }
      val durations = workloadHistory.mapNotNull { metricsCalculator.getDeploymentDuration(it) }
      val avgLeadTime = if (durations.isNotEmpty()) durations.average() else 0.0
      val failureRate = if (workloadHistory.isNotEmpty()) {
        (failureCount.toDouble() / workloadHistory.size) * 100
      } else {
        0.0
      }

      // Calculate a simple grade based on failure rate and lead time
      val cfrGrade = metricsCalculator.gradeChangeFailureRate(failureRate)
      val ltGrade = metricsCalculator.gradeLeadTime(avgLeadTime)
      val overallGrade = if (cfrGrade.ordinal < ltGrade.ordinal) ltGrade else cfrGrade

      WorkloadSummaryMetrics(
        workloadId = workload.id ?: 0,
        workloadName = workload.name ?: "",
        workloadKind = workload.kind ?: "",
        deploymentCount = workloadHistory.size,
        failureCount = failureCount,
        failureRate = failureRate,
        averageLeadTimeSeconds = avgLeadTime,
        performanceGrade = overallGrade
      )
    }.sortedByDescending { it.deploymentCount }
  }

  private fun calculateTeamComparison(teamName: String, filters: MetricsFilters): TeamComparisonData {
    val allScorecards = getAllTeamScorecards(filters)
    val teamSummary = allScorecards.teams.find { it.team == teamName }

    return if (teamSummary != null) {
      val totalTeams = allScorecards.teams.size
      val rank = teamSummary.rank
      val percentile = if (totalTeams > 1) {
        ((totalTeams - rank).toDouble() / (totalTeams - 1)) * 100
      } else {
        100.0
      }
      val avgOrdinal = allScorecards.teams.map { it.overallGrade.ordinal }.average()

      TeamComparisonData(
        teamRank = rank,
        totalTeams = totalTeams,
        percentile = percentile,
        aboveAverage = teamSummary.overallGrade.ordinal <= avgOrdinal
      )
    } else {
      TeamComparisonData(
        teamRank = 0,
        totalTeams = allScorecards.teams.size,
        percentile = 0.0,
        aboveAverage = false
      )
    }
  }
}
