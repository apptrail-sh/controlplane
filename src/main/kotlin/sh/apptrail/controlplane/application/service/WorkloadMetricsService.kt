package sh.apptrail.controlplane.application.service

import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionHistoryRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadInstanceRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Service
class WorkloadMetricsService(
  private val workloadRepository: WorkloadRepository,
  private val workloadInstanceRepository: WorkloadInstanceRepository,
  private val versionHistoryRepository: VersionHistoryRepository,
  private val metricsCalculator: MetricsCalculator
) {

  fun getWorkloadMetrics(
    workloadId: Long,
    filters: MetricsFilters
  ): WorkloadMetricsResponse? {
    val workload = workloadRepository.findById(workloadId).orElse(null) ?: return null
    val instances = workloadInstanceRepository.findByWorkloadId(workloadId)

    if (instances.isEmpty()) {
      return createEmptyMetricsResponse(
        workloadId = workloadId,
        workloadName = workload.name ?: "",
        workloadKind = workload.kind ?: "",
        team = workload.team,
        filters = filters
      )
    }

    val instanceIds = instances.mapNotNull { it.id }
    var history = versionHistoryRepository.findByInstanceIdsAndDateRange(
      instanceIds = instanceIds,
      startDate = filters.startDate,
      endDate = filters.endDate
    )

    // Apply optional filters
    if (filters.environment != null) {
      val envInstances = instances.filter { it.environment == filters.environment }
      val envInstanceIds = envInstances.mapNotNull { it.id }.toSet()
      history = history.filter { it.workloadInstance.id in envInstanceIds }
    }
    if (filters.clusterId != null) {
      history = history.filter { it.workloadInstance.cluster.id == filters.clusterId }
    }

    // Calculate core DORA metrics
    val deploymentFrequency = metricsCalculator.calculateDeploymentFrequency(
      history, filters.startDate, filters.endDate
    )
    val leadTime = metricsCalculator.calculateLeadTime(history)
    val changeFailureRate = metricsCalculator.calculateChangeFailureRate(history)
    val mttr = metricsCalculator.calculateMTTR(history)

    // Calculate grades
    val performanceGrade = WorkloadPerformanceGrade(
      overall = metricsCalculator.calculateOverallGrade(
        metricsCalculator.gradeDeploymentFrequency(deploymentFrequency.deploymentsPerDay),
        metricsCalculator.gradeLeadTime(leadTime.averageSeconds),
        metricsCalculator.gradeChangeFailureRate(changeFailureRate.failureRate),
        metricsCalculator.gradeMTTR(mttr.averageSeconds)
      ),
      deploymentFrequency = metricsCalculator.gradeDeploymentFrequency(deploymentFrequency.deploymentsPerDay),
      leadTime = metricsCalculator.gradeLeadTime(leadTime.averageSeconds),
      changeFailureRate = metricsCalculator.gradeChangeFailureRate(changeFailureRate.failureRate),
      mttr = metricsCalculator.gradeMTTR(mttr.averageSeconds)
    )

    // Calculate trends
    val trends = calculateTrends(history, filters.granularity)

    // Calculate instance breakdown
    val instanceBreakdown = calculateInstanceBreakdown(history, instances)

    return WorkloadMetricsResponse(
      workloadId = workloadId,
      workloadName = workload.name ?: "",
      workloadKind = workload.kind ?: "",
      team = workload.team,
      deploymentFrequency = deploymentFrequency,
      leadTime = leadTime,
      changeFailureRate = changeFailureRate,
      mttr = mttr,
      performanceGrade = performanceGrade,
      trends = trends,
      instanceBreakdown = instanceBreakdown,
      dateRange = DateRange(filters.startDate, filters.endDate)
    )
  }

  private fun calculateTrends(
    history: List<VersionHistoryEntity>,
    granularity: String
  ): List<WorkloadMetricsTrend> {
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
      .map { (periodStart, entries) ->
        val periodEnd = when (granularity) {
          "hour" -> periodStart.plus(1, ChronoUnit.HOURS)
          "week" -> periodStart.plus(7, ChronoUnit.DAYS)
          "month" -> {
            val date = LocalDate.ofInstant(periodStart, ZoneOffset.UTC)
            date.plusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC)
          }
          else -> periodStart.plus(1, ChronoUnit.DAYS)
        }

        val successCount = entries.count { it.deploymentPhase == "completed" }
        val failureCount = entries.count { metricsCalculator.isFailed(it) }
        val rollbackCount = entries.count { metricsCalculator.isRollback(it) }
        val durations = entries.mapNotNull { metricsCalculator.getDeploymentDuration(it) }
        val avgLeadTime = if (durations.isNotEmpty()) durations.average() else 0.0

        WorkloadMetricsTrend(
          period = periodStart.toString(),
          periodStart = periodStart,
          periodEnd = periodEnd,
          deploymentCount = entries.size,
          successCount = successCount,
          failureCount = failureCount,
          rollbackCount = rollbackCount,
          averageLeadTimeSeconds = avgLeadTime
        )
      }
      .sortedByDescending { it.periodStart }
  }

  private fun calculateInstanceBreakdown(
    history: List<VersionHistoryEntity>,
    instances: List<sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadInstanceEntity>
  ): List<InstanceMetricsBreakdown> {
    val instanceMap = instances.associateBy { it.id }

    return history
      .groupBy { it.workloadInstance.id }
      .mapNotNull { (instanceId, entries) ->
        val instance = instanceMap[instanceId] ?: return@mapNotNull null
        val failureCount = entries.count { metricsCalculator.isFailed(it) }
        val durations = entries.mapNotNull { metricsCalculator.getDeploymentDuration(it) }

        InstanceMetricsBreakdown(
          instanceId = instanceId ?: 0,
          clusterId = instance.cluster.id ?: 0,
          clusterName = instance.cluster.name,
          namespace = instance.namespace,
          environment = instance.environment,
          deploymentCount = entries.size,
          failureCount = failureCount,
          failureRate = if (entries.isNotEmpty()) (failureCount.toDouble() / entries.size) * 100 else 0.0,
          averageLeadTimeSeconds = if (durations.isNotEmpty()) durations.average() else 0.0
        )
      }
      .sortedByDescending { it.deploymentCount }
  }

  private fun createEmptyMetricsResponse(
    workloadId: Long,
    workloadName: String,
    workloadKind: String,
    team: String?,
    filters: MetricsFilters
  ): WorkloadMetricsResponse {
    return WorkloadMetricsResponse(
      workloadId = workloadId,
      workloadName = workloadName,
      workloadKind = workloadKind,
      team = team,
      deploymentFrequency = DeploymentFrequencyMetrics(0, 0.0, 0),
      leadTime = LeadTimeMetricsResponse(0.0, 0, 0, 0, 0, 0, 0),
      changeFailureRate = ChangeFailureRateMetricsResponse(0, 0, 0.0),
      mttr = MTTRMetricsResponse(0.0, 0, 0, 0, 0),
      performanceGrade = WorkloadPerformanceGrade(
        PerformanceGrade.LOW,
        PerformanceGrade.LOW,
        PerformanceGrade.LOW,
        PerformanceGrade.LOW,
        PerformanceGrade.LOW
      ),
      trends = emptyList(),
      instanceBreakdown = emptyList(),
      dateRange = DateRange(filters.startDate, filters.endDate)
    )
  }
}
