package sh.apptrail.controlplane.application.service

import java.time.Instant

/**
 * Response for workload-level DORA metrics.
 */
data class WorkloadMetricsResponse(
  val workloadId: Long,
  val workloadName: String,
  val workloadKind: String,
  val team: String?,
  val deploymentFrequency: DeploymentFrequencyMetrics,
  val leadTime: LeadTimeMetricsResponse,
  val changeFailureRate: ChangeFailureRateMetricsResponse,
  val mttr: MTTRMetricsResponse,
  val performanceGrade: WorkloadPerformanceGrade,
  val trends: List<WorkloadMetricsTrend>,
  val instanceBreakdown: List<InstanceMetricsBreakdown>,
  val dateRange: DateRange
)

/**
 * Performance grades for a workload.
 */
data class WorkloadPerformanceGrade(
  val overall: PerformanceGrade,
  val deploymentFrequency: PerformanceGrade,
  val leadTime: PerformanceGrade,
  val changeFailureRate: PerformanceGrade,
  val mttr: PerformanceGrade
)

/**
 * Trend data for a specific time period.
 */
data class WorkloadMetricsTrend(
  val period: String,
  val periodStart: Instant,
  val periodEnd: Instant,
  val deploymentCount: Int,
  val successCount: Int,
  val failureCount: Int,
  val rollbackCount: Int,
  val averageLeadTimeSeconds: Double
)

/**
 * Metrics breakdown by instance.
 */
data class InstanceMetricsBreakdown(
  val instanceId: Long,
  val clusterId: Long,
  val clusterName: String,
  val namespace: String,
  val environment: String,
  val deploymentCount: Int,
  val failureCount: Int,
  val failureRate: Double,
  val averageLeadTimeSeconds: Double
)

/**
 * Date range for metrics queries.
 */
data class DateRange(
  val startDate: Instant,
  val endDate: Instant
)

/**
 * Team scorecard response with DORA metrics and performance grades.
 */
data class TeamScorecardResponse(
  val team: String,
  val performanceGrade: TeamPerformanceGrade,
  val doraMetrics: TeamDoraMetrics,
  val workloadBreakdown: List<WorkloadSummaryMetrics>,
  val comparison: TeamComparisonData,
  val dateRange: DateRange
)

/**
 * Performance grades for a team.
 */
data class TeamPerformanceGrade(
  val overall: PerformanceGrade,
  val deploymentFrequency: PerformanceGrade,
  val leadTime: PerformanceGrade,
  val changeFailureRate: PerformanceGrade,
  val mttr: PerformanceGrade
)

/**
 * DORA metrics for a team.
 */
data class TeamDoraMetrics(
  val deploymentFrequency: DeploymentFrequencyMetrics,
  val leadTime: LeadTimeMetricsResponse,
  val changeFailureRate: ChangeFailureRateMetricsResponse,
  val mttr: MTTRMetricsResponse
)

/**
 * Summary metrics for a single workload within a team.
 */
data class WorkloadSummaryMetrics(
  val workloadId: Long,
  val workloadName: String,
  val workloadKind: String,
  val deploymentCount: Int,
  val failureCount: Int,
  val failureRate: Double,
  val averageLeadTimeSeconds: Double,
  val performanceGrade: PerformanceGrade
)

/**
 * Comparison data for a team against others.
 */
data class TeamComparisonData(
  val teamRank: Int,
  val totalTeams: Int,
  val percentile: Double,
  val aboveAverage: Boolean
)

/**
 * Team scorecard summary for leaderboard/comparison views.
 */
data class TeamScorecardSummary(
  val team: String,
  val overallGrade: PerformanceGrade,
  val deploymentFrequencyGrade: PerformanceGrade,
  val leadTimeGrade: PerformanceGrade,
  val changeFailureRateGrade: PerformanceGrade,
  val mttrGrade: PerformanceGrade,
  val totalDeployments: Int,
  val failureRate: Double,
  val averageLeadTimeSeconds: Double,
  val workloadCount: Int,
  val rank: Int
)

/**
 * Response for team scorecards leaderboard.
 */
data class TeamScorecardsResponse(
  val teams: List<TeamScorecardSummary>,
  val dateRange: DateRange,
  val totalDeployments: Int,
  val overallFailureRate: Double,
  val overallAverageLeadTimeSeconds: Double
)

/**
 * Filters for metrics queries.
 */
data class MetricsFilters(
  val startDate: Instant,
  val endDate: Instant,
  val environment: String? = null,
  val clusterId: Long? = null,
  val granularity: String = "day"
)
