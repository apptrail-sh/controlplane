package sh.apptrail.controlplane.application.service

import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionHistoryRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadInstanceRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Service
class AnalyticsService(
  private val versionHistoryRepository: VersionHistoryRepository,
  private val workloadInstanceRepository: WorkloadInstanceRepository,
  private val workloadRepository: WorkloadRepository,
) {
  fun getAvailableFilters(): AnalyticsFiltersResponse {
    val instances = workloadInstanceRepository.findAll()
    val workloads = workloadRepository.findAll()

    val environments = instances.mapNotNull { it.environment }.distinct().sorted()
    val teams = workloads.mapNotNull { it.team }.distinct().sorted()

    return AnalyticsFiltersResponse(
      environments = environments,
      teams = teams
    )
  }

  fun getOverview(filters: AnalyticsFilters): DashboardOverviewResponse {
    val allHistory = versionHistoryRepository.findAll()
    val allInstances = workloadInstanceRepository.findAll()
    val allWorkloads = workloadRepository.findAll()

    // Apply filters
    val filteredHistory = filterHistory(allHistory, allInstances, allWorkloads, filters)

    // Calculate metrics
    val deploymentFrequency = calculateDeploymentFrequency(filteredHistory, allInstances, filters)
    val leadTime = calculateLeadTime(filteredHistory)
    val mttr = calculateMTTR(filteredHistory)
    val changeFailureRate = calculateChangeFailureRate(filteredHistory)
    val slowestDeployments = calculateSlowestDeployments(filteredHistory, allInstances, allWorkloads)
    val mostFrequentRollbacks = calculateMostFrequentRollbacks(filteredHistory, allInstances, allWorkloads)
    val teamPerformance = calculateTeamPerformance(filteredHistory, allInstances, allWorkloads)
    val deploymentTrends = calculateDeploymentTrends(filteredHistory, filters)

    return DashboardOverviewResponse(
      deploymentFrequency = deploymentFrequency,
      leadTime = leadTime,
      mttr = mttr,
      changeFailureRate = changeFailureRate,
      slowestDeployments = slowestDeployments,
      mostFrequentRollbacks = mostFrequentRollbacks,
      teamPerformance = teamPerformance,
      deploymentTrends = deploymentTrends
    )
  }

  private fun filterHistory(
    history: List<sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity>,
    instances: List<sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadInstanceEntity>,
    workloads: List<sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadEntity>,
    filters: AnalyticsFilters
  ): List<sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity> {
    val instanceMap = instances.associateBy { it.id }
    val workloadMap = workloads.associateBy { it.id }

    return history.filter { entry ->
      val instance = instanceMap[entry.workloadInstance.id] ?: return@filter false
      val workload = workloadMap[instance.workload.id] ?: return@filter false

      // Date range filter - supports both ISO datetime (2026-01-08T02:00:00.000Z) and date-only (2026-01-08)
      val startDate = filters.startDate?.let {
        if (it.contains("T")) Instant.parse(it)
        else LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC)
      }
      val endDate = filters.endDate?.let {
        if (it.contains("T")) Instant.parse(it)
        else LocalDate.parse(it).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
      }
      if (startDate != null && entry.detectedAt.isBefore(startDate)) return@filter false
      if (endDate != null && entry.detectedAt.isAfter(endDate)) return@filter false

      // Environment filter
      if (filters.environment != null && instance.environment != filters.environment) return@filter false

      // Team filter
      if (filters.team != null && workload.team != filters.team) return@filter false

      // Workload filter
      if (filters.workloadId != null && workload.id != filters.workloadId) return@filter false

      // Cluster filter
      if (filters.clusterId != null && instance.cluster.id != filters.clusterId) return@filter false

      true
    }
  }

  private fun calculateDeploymentFrequency(
    history: List<sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity>,
    instances: List<sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadInstanceEntity>,
    filters: AnalyticsFilters
  ): List<DeploymentFrequencyResponse> {
    val instanceMap = instances.associateBy { it.id }

    return history
      .groupBy { entry ->
        val instance = instanceMap[entry.workloadInstance.id]
        instance?.environment ?: "unknown"
      }
      .map { (environment, entries) ->
        DeploymentFrequencyResponse(
          period = "total",
          count = entries.size,
          environment = environment,
          team = "all"
        )
      }
  }

  // Helper function to get deployment duration - uses deploymentDurationSeconds if available,
  // otherwise calculates from deploymentStartedAt and deploymentCompletedAt
  private fun getDeploymentDuration(entry: sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity): Int? {
    // First try the explicit duration field
    entry.deploymentDurationSeconds?.let { return it }

    // Calculate from timestamps if both are available
    val startedAt = entry.deploymentStartedAt ?: return null
    val completedAt = entry.deploymentCompletedAt ?: return null

    return java.time.Duration.between(startedAt, completedAt).seconds.toInt()
  }

  private fun calculateLeadTime(
    history: List<sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity>
  ): LeadTimeMetricsResponse {
    val durations = history.mapNotNull { getDeploymentDuration(it) }

    if (durations.isEmpty()) {
      return LeadTimeMetricsResponse(0.0, 0, 0, 0, 0, 0, 0)
    }

    val sorted = durations.sorted()
    val average = durations.average()
    val median = if (sorted.size % 2 == 0) {
      (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    } else {
      sorted[sorted.size / 2]
    }
    val p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)]
    val p99 = sorted[(sorted.size * 0.99).toInt().coerceAtMost(sorted.size - 1)]

    return LeadTimeMetricsResponse(
      averageSeconds = average,
      medianSeconds = median,
      p95Seconds = p95,
      p99Seconds = p99,
      minSeconds = sorted.firstOrNull() ?: 0,
      maxSeconds = sorted.lastOrNull() ?: 0,
      sampleSize = sorted.size
    )
  }

  /**
   * Calculate MTTR (Mean Time To Recovery) metrics.
   * Tracks failure→recovery sequences per workload instance.
   * A recovery only counts when a failure occurred for that workload and was subsequently resolved.
   */
  private fun calculateMTTR(
    history: List<sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity>
  ): MTTRMetricsResponse {
    // Group by workload instance to track sequences per workload
    val byWorkload = history.groupBy { it.workloadInstance.id }

    var totalFailures = 0
    var totalRecoveries = 0
    val recoveryDurations = mutableListOf<Long>()

    for ((_, workloadHistory) in byWorkload) {
      // Sort by time to track sequences
      val sorted = workloadHistory.sortedBy { it.detectedAt }
      var pendingFailure: sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity? = null

      for (entry in sorted) {
        when {
          entry.deploymentPhase == "failed" -> {
            // New failure for this workload (only count if no pending failure)
            if (pendingFailure == null) {
              totalFailures++
            }
            pendingFailure = entry
          }
          pendingFailure != null && entry.deploymentPhase == "completed" -> {
            // Recovery from failure for this workload
            totalRecoveries++
            // MTTR = time from failure detection to recovery completion
            val recoveryTime = Duration.between(
              pendingFailure.detectedAt,
              entry.deploymentCompletedAt ?: entry.detectedAt
            ).seconds
            recoveryDurations.add(recoveryTime)
            pendingFailure = null
          }
        }
      }
    }

    val mttrSeconds = if (recoveryDurations.isNotEmpty()) {
      recoveryDurations.average()
    } else {
      0.0
    }

    val sortedDurations = recoveryDurations.map { it.toInt() }.sorted()
    val median = if (sortedDurations.size % 2 == 0 && sortedDurations.isNotEmpty()) {
      (sortedDurations[sortedDurations.size / 2 - 1] + sortedDurations[sortedDurations.size / 2]) / 2
    } else if (sortedDurations.isNotEmpty()) {
      sortedDurations[sortedDurations.size / 2]
    } else {
      0
    }
    val p95 = if (sortedDurations.isNotEmpty()) {
      sortedDurations[(sortedDurations.size * 0.95).toInt().coerceAtMost(sortedDurations.size - 1)]
    } else {
      0
    }

    return MTTRMetricsResponse(
      averageSeconds = mttrSeconds,
      medianSeconds = median,
      p95Seconds = p95,
      totalFailures = totalFailures,
      totalRestores = totalRecoveries
    )
  }

  private fun calculateChangeFailureRate(
    history: List<sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity>
  ): ChangeFailureRateMetricsResponse {
    val total = history.size
    val failed = history.count { it.deploymentPhase == "failed" }
    val rate = if (total > 0) (failed.toDouble() / total) * 100 else 0.0

    return ChangeFailureRateMetricsResponse(
      totalDeployments = total,
      failedDeployments = failed,
      failureRate = rate
    )
  }

  private fun calculateSlowestDeployments(
    history: List<sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity>,
    instances: List<sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadInstanceEntity>,
    workloads: List<sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadEntity>
  ): List<SlowestDeploymentResponse> {
    val instanceMap = instances.associateBy { it.id }
    val workloadMap = workloads.associateBy { it.id }

    return history
      .filter { getDeploymentDuration(it) != null }
      .groupBy { entry ->
        val instance = instanceMap[entry.workloadInstance.id]
        instance?.workload?.id
      }
      .mapNotNull { (workloadId, entries) ->
        val workload = workloadMap[workloadId] ?: return@mapNotNull null
        val durations = entries.mapNotNull { getDeploymentDuration(it) }

        SlowestDeploymentResponse(
          workloadId = workloadId ?: 0,
          workloadName = workload.name ?: "unknown",
          workloadKind = workload.kind ?: "unknown",
          team = workload.team ?: "unassigned",
          averageDurationSeconds = durations.average().toInt(),
          maxDurationSeconds = durations.maxOrNull() ?: 0,
          deploymentCount = entries.size
        )
      }
      .sortedByDescending { it.averageDurationSeconds }
      .take(10)
  }

  private fun calculateMostFrequentRollbacks(
    history: List<sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity>,
    instances: List<sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadInstanceEntity>,
    workloads: List<sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadEntity>
  ): List<RollbackFrequencyResponse> {
    val instanceMap = instances.associateBy { it.id }
    val workloadMap = workloads.associateBy { it.id }

    val rollbacks = history.filter {
      it.previousVersion != null && it.currentVersion < (it.previousVersion ?: "")
    }

    return rollbacks
      .groupBy { entry ->
        val instance = instanceMap[entry.workloadInstance.id]
        instance?.workload?.id
      }
      .mapNotNull { (workloadId, entries) ->
        val workload = workloadMap[workloadId] ?: return@mapNotNull null
        val totalDeployments = history.count {
          val instance = instanceMap[it.workloadInstance.id]
          instance?.workload?.id == workloadId
        }

        RollbackFrequencyResponse(
          workloadId = workloadId ?: 0,
          workloadName = workload.name ?: "unknown",
          workloadKind = workload.kind ?: "unknown",
          team = workload.team ?: "unassigned",
          rollbackCount = entries.size,
          deploymentCount = totalDeployments,
          rollbackRate = if (totalDeployments > 0) (entries.size.toDouble() / totalDeployments) * 100 else 0.0
        )
      }
      .sortedByDescending { it.rollbackCount }
      .take(10)
  }

  private fun calculateTeamPerformance(
    history: List<sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity>,
    instances: List<sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadInstanceEntity>,
    workloads: List<sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadEntity>
  ): List<TeamPerformanceResponse> {
    val instanceMap = instances.associateBy { it.id }
    val workloadMap = workloads.associateBy { it.id }

    return history
      .groupBy { entry ->
        val instance = instanceMap[entry.workloadInstance.id]
        val workload = workloadMap[instance?.workload?.id]
        workload?.team ?: "unassigned"
      }
      .map { (team, entries) ->
        val failed = entries.count { it.deploymentPhase == "failed" }
        val rollbacks = entries.count {
          it.previousVersion != null && it.currentVersion < (it.previousVersion ?: "")
        }
        val durations = entries.mapNotNull { getDeploymentDuration(it) }
        val avgDuration = if (durations.isNotEmpty()) durations.average().toInt() else 0

        TeamPerformanceResponse(
          team = team,
          totalDeployments = entries.size,
          failedDeployments = failed,
          failureRate = if (entries.isNotEmpty()) (failed.toDouble() / entries.size) * 100 else 0.0,
          averageLeadTimeSeconds = avgDuration,
          averageDurationSeconds = avgDuration,
          rollbackCount = rollbacks
        )
      }
      .sortedByDescending { it.totalDeployments }
  }

  private fun calculateDeploymentTrends(
    history: List<sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity>,
    filters: AnalyticsFilters
  ): List<DeploymentTrendResponse> {
    val granularity = filters.granularity ?: "day"

    return history
      .groupBy { entry ->
        when (granularity) {
          "hour" -> entry.detectedAt.truncatedTo(ChronoUnit.HOURS)
          "week" -> {
            val date = LocalDate.ofInstant(entry.detectedAt, ZoneOffset.UTC)
            date.minusDays(date.dayOfWeek.value.toLong() - 1).atStartOfDay().toInstant(ZoneOffset.UTC)
          }
          "month" -> {
            val date = LocalDate.ofInstant(entry.detectedAt, ZoneOffset.UTC)
            date.withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC)
          }
          else -> entry.detectedAt.truncatedTo(ChronoUnit.DAYS)
        }
      }
      .map { (period, entries) ->
        val success = entries.count { it.deploymentPhase == "completed" }
        val failed = entries.count { it.deploymentPhase == "failed" }
        val durations = entries.mapNotNull { getDeploymentDuration(it) }

        DeploymentTrendResponse(
          period = period.toString(),
          deploymentCount = entries.size,
          successCount = success,
          failureCount = failed,
          averageDurationSeconds = if (durations.isNotEmpty()) durations.average().toInt() else 0,
          averageLeadTimeSeconds = if (durations.isNotEmpty()) durations.average().toInt() else 0
        )
      }
      .sortedByDescending { it.period }
  }
}

// DTOs
data class AnalyticsFilters(
  val startDate: String? = null,
  val endDate: String? = null,
  val environment: String? = null,
  val team: String? = null,
  val workloadId: Long? = null,
  val clusterId: Long? = null,
  val granularity: String? = "day"
)

data class AnalyticsFiltersResponse(
  val environments: List<String>,
  val teams: List<String>
)

data class DashboardOverviewResponse(
  val deploymentFrequency: List<DeploymentFrequencyResponse>,
  val leadTime: LeadTimeMetricsResponse,
  val mttr: MTTRMetricsResponse,
  val changeFailureRate: ChangeFailureRateMetricsResponse,
  val slowestDeployments: List<SlowestDeploymentResponse>,
  val mostFrequentRollbacks: List<RollbackFrequencyResponse>,
  val teamPerformance: List<TeamPerformanceResponse>,
  val deploymentTrends: List<DeploymentTrendResponse>
)

data class DeploymentFrequencyResponse(
  val period: String,
  val count: Int,
  val environment: String,
  val team: String
)

data class LeadTimeMetricsResponse(
  val averageSeconds: Double,
  val medianSeconds: Int,
  val p95Seconds: Int,
  val p99Seconds: Int,
  val minSeconds: Int,
  val maxSeconds: Int,
  val sampleSize: Int
)

data class MTTRMetricsResponse(
  val averageSeconds: Double,
  val medianSeconds: Int,
  val p95Seconds: Int,
  val totalFailures: Int,
  val totalRestores: Int
)

data class ChangeFailureRateMetricsResponse(
  val totalDeployments: Int,
  val failedDeployments: Int,
  val failureRate: Double
)

data class SlowestDeploymentResponse(
  val workloadId: Long,
  val workloadName: String,
  val workloadKind: String,
  val team: String,
  val averageDurationSeconds: Int,
  val maxDurationSeconds: Int,
  val deploymentCount: Int
)

data class RollbackFrequencyResponse(
  val workloadId: Long,
  val workloadName: String,
  val workloadKind: String,
  val team: String,
  val rollbackCount: Int,
  val deploymentCount: Int,
  val rollbackRate: Double
)

data class TeamPerformanceResponse(
  val team: String,
  val totalDeployments: Int,
  val failedDeployments: Int,
  val failureRate: Double,
  val averageLeadTimeSeconds: Int,
  val averageDurationSeconds: Int,
  val rollbackCount: Int
)

data class DeploymentTrendResponse(
  val period: String,
  val deploymentCount: Int,
  val successCount: Int,
  val failureCount: Int,
  val averageDurationSeconds: Int,
  val averageLeadTimeSeconds: Int
)
