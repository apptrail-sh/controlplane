package sh.apptrail.controlplane.application.service

import org.springframework.stereotype.Component
import sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Utility class for calculating DORA metrics from version history data.
 * Provides reusable calculation methods for deployment frequency, lead time, MTTR, and change failure rate.
 */
@Component
class MetricsCalculator {

  /**
   * Get deployment duration from a version history entry.
   * Prefers explicit deploymentDurationSeconds field, otherwise calculates from timestamps.
   */
  fun getDeploymentDuration(entry: VersionHistoryEntity): Int? {
    entry.deploymentDurationSeconds?.let { return it }
    val startedAt = entry.deploymentStartedAt ?: return null
    val completedAt = entry.deploymentCompletedAt ?: return null
    return Duration.between(startedAt, completedAt).seconds.toInt()
  }

  /**
   * Determine if a deployment represents a rollback.
   * A rollback is when the current version is lexicographically less than the previous version.
   */
  fun isRollback(entry: VersionHistoryEntity): Boolean {
    val previous = entry.previousVersion ?: return false
    return entry.currentVersion < previous
  }

  /**
   * Determine if a deployment failed.
   */
  fun isFailed(entry: VersionHistoryEntity): Boolean {
    return entry.deploymentPhase == "failed"
  }

  /**
   * Calculate lead time metrics from a list of version history entries.
   */
  fun calculateLeadTime(history: List<VersionHistoryEntity>): LeadTimeMetricsResponse {
    val durations = history.mapNotNull { getDeploymentDuration(it) }

    if (durations.isEmpty()) {
      return LeadTimeMetricsResponse(0.0, 0, 0, 0, 0, 0, 0)
    }

    val sorted = durations.sorted()
    val average = durations.average()
    val median = calculateMedian(sorted)
    val p95 = calculatePercentile(sorted, 0.95)
    val p99 = calculatePercentile(sorted, 0.99)

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
  fun calculateMTTR(history: List<VersionHistoryEntity>): MTTRMetricsResponse {
    // Group by workload instance to track sequences per workload
    val byWorkload = history.groupBy { it.workloadInstance.id }

    var totalFailures = 0
    var totalRecoveries = 0
    val recoveryDurations = mutableListOf<Long>()

    for ((_, workloadHistory) in byWorkload) {
      // Sort by time to track sequences
      val sorted = workloadHistory.sortedBy { it.detectedAt }
      var pendingFailure: VersionHistoryEntity? = null

      for (entry in sorted) {
        when {
          isFailed(entry) -> {
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
    val median = if (sortedDurations.isNotEmpty()) calculateMedian(sortedDurations) else 0
    val p95 = if (sortedDurations.isNotEmpty()) calculatePercentile(sortedDurations, 0.95) else 0

    return MTTRMetricsResponse(
      averageSeconds = mttrSeconds,
      medianSeconds = median,
      p95Seconds = p95,
      totalFailures = totalFailures,
      totalRestores = totalRecoveries
    )
  }

  /**
   * Calculate change failure rate metrics.
   */
  fun calculateChangeFailureRate(history: List<VersionHistoryEntity>): ChangeFailureRateMetricsResponse {
    val total = history.size
    val failed = history.count { isFailed(it) }
    val rate = if (total > 0) (failed.toDouble() / total) * 100 else 0.0

    return ChangeFailureRateMetricsResponse(
      totalDeployments = total,
      failedDeployments = failed,
      failureRate = rate
    )
  }

  /**
   * Calculate deployment frequency metrics.
   */
  fun calculateDeploymentFrequency(
    history: List<VersionHistoryEntity>,
    startDate: Instant,
    endDate: Instant
  ): DeploymentFrequencyMetrics {
    val totalDeployments = history.size
    val daysBetween = ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(1)
    val deploymentsPerDay = totalDeployments.toDouble() / daysBetween

    return DeploymentFrequencyMetrics(
      totalDeployments = totalDeployments,
      deploymentsPerDay = deploymentsPerDay,
      daysCovered = daysBetween.toInt()
    )
  }

  /**
   * Grade deployment frequency based on DORA benchmarks.
   * Elite: Multiple per day (>1/day)
   * High: Weekly to daily (0.14-1/day)
   * Medium: Monthly to weekly (0.03-0.14/day)
   * Low: Less than monthly (<0.03/day)
   */
  fun gradeDeploymentFrequency(deploymentsPerDay: Double): PerformanceGrade {
    return when {
      deploymentsPerDay >= 1.0 -> PerformanceGrade.ELITE
      deploymentsPerDay >= 0.14 -> PerformanceGrade.HIGH
      deploymentsPerDay >= 0.03 -> PerformanceGrade.MEDIUM
      else -> PerformanceGrade.LOW
    }
  }

  /**
   * Grade lead time based on DORA benchmarks.
   * Elite: < 1 hour (3600 seconds)
   * High: < 1 week (604800 seconds)
   * Medium: < 1 month (2592000 seconds)
   * Low: > 1 month
   */
  fun gradeLeadTime(averageSeconds: Double): PerformanceGrade {
    return when {
      averageSeconds < 3600 -> PerformanceGrade.ELITE
      averageSeconds < 604800 -> PerformanceGrade.HIGH
      averageSeconds < 2592000 -> PerformanceGrade.MEDIUM
      else -> PerformanceGrade.LOW
    }
  }

  /**
   * Grade change failure rate based on DORA benchmarks.
   * Elite: 0-15%
   * High: 16-30%
   * Medium: 31-45%
   * Low: > 45%
   */
  fun gradeChangeFailureRate(failureRate: Double): PerformanceGrade {
    return when {
      failureRate <= 15 -> PerformanceGrade.ELITE
      failureRate <= 30 -> PerformanceGrade.HIGH
      failureRate <= 45 -> PerformanceGrade.MEDIUM
      else -> PerformanceGrade.LOW
    }
  }

  /**
   * Grade MTTR based on DORA benchmarks.
   * Elite: < 1 hour (3600 seconds)
   * High: < 1 day (86400 seconds)
   * Medium: < 1 week (604800 seconds)
   * Low: > 1 week
   */
  fun gradeMTTR(averageSeconds: Double): PerformanceGrade {
    return when {
      averageSeconds < 3600 -> PerformanceGrade.ELITE
      averageSeconds < 86400 -> PerformanceGrade.HIGH
      averageSeconds < 604800 -> PerformanceGrade.MEDIUM
      else -> PerformanceGrade.LOW
    }
  }

  /**
   * Calculate overall performance grade from individual metric grades.
   */
  fun calculateOverallGrade(
    deploymentFrequencyGrade: PerformanceGrade,
    leadTimeGrade: PerformanceGrade,
    changeFailureRateGrade: PerformanceGrade,
    mttrGrade: PerformanceGrade
  ): PerformanceGrade {
    val grades = listOf(deploymentFrequencyGrade, leadTimeGrade, changeFailureRateGrade, mttrGrade)
    val avgOrdinal = grades.map { it.ordinal }.average()

    return when {
      avgOrdinal < 0.5 -> PerformanceGrade.ELITE
      avgOrdinal < 1.5 -> PerformanceGrade.HIGH
      avgOrdinal < 2.5 -> PerformanceGrade.MEDIUM
      else -> PerformanceGrade.LOW
    }
  }

  private fun calculateMedian(sorted: List<Int>): Int {
    return if (sorted.size % 2 == 0) {
      (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    } else {
      sorted[sorted.size / 2]
    }
  }

  private fun calculatePercentile(sorted: List<Int>, percentile: Double): Int {
    val index = (sorted.size * percentile).toInt().coerceAtMost(sorted.size - 1)
    return sorted[index]
  }
}

/**
 * Performance grades based on DORA research.
 */
enum class PerformanceGrade {
  ELITE,
  HIGH,
  MEDIUM,
  LOW
}

/**
 * Deployment frequency metrics.
 */
data class DeploymentFrequencyMetrics(
  val totalDeployments: Int,
  val deploymentsPerDay: Double,
  val daysCovered: Int
)
