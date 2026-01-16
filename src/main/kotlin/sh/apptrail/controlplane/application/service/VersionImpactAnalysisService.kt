package sh.apptrail.controlplane.application.service

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.config.MetricConfig
import sh.apptrail.controlplane.infrastructure.config.VersionImpactAnalysisProperties
import sh.apptrail.controlplane.infrastructure.metrics.PrometheusClient
import sh.apptrail.controlplane.infrastructure.persistence.entity.*
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionHistoryRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionImpactAnalysisRepository
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors

@Service
@ConditionalOnProperty(prefix = "app.version-impact-analysis", name = ["enabled"], havingValue = "true")
class VersionImpactAnalysisService(
  private val analysisRepository: VersionImpactAnalysisRepository,
  private val versionHistoryRepository: VersionHistoryRepository,
  private val prometheusClient: PrometheusClient?,
  private val properties: VersionImpactAnalysisProperties
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val executor = Executors.newFixedThreadPool(properties.maxConcurrentAnalyses)

  @Transactional
  fun scheduleAnalysis(versionHistoryId: Long): VersionImpactAnalysisEntity? {
    val versionHistory = versionHistoryRepository.findById(versionHistoryId).orElse(null)
    if (versionHistory == null) {
      log.warn("Cannot schedule analysis: VersionHistory {} not found", versionHistoryId)
      return null
    }

    val existing = analysisRepository.findByVersionHistoryId(versionHistoryId)
    if (existing != null) {
      log.debug("Analysis already exists for VersionHistory {}", versionHistoryId)
      return existing
    }

    val completedAt = versionHistory.deploymentCompletedAt
    if (completedAt == null) {
      log.warn("Cannot schedule analysis: deployment not completed for VersionHistory {}", versionHistoryId)
      return null
    }

    val scheduledAt = completedAt.plus(Duration.ofMinutes(properties.delayMinutes))

    val analysis = VersionImpactAnalysisEntity().apply {
      this.versionHistory = versionHistory
      this.status = ImpactAnalysisStatus.PENDING
      this.scheduledAt = scheduledAt
    }

    log.info(
      "Scheduling version impact analysis for VersionHistory {} at {}",
      versionHistoryId,
      scheduledAt
    )
    return analysisRepository.save(analysis)
  }

  @Scheduled(fixedDelayString = "\${app.version-impact-analysis.poll-interval-seconds:60}000")
  fun processPendingAnalyses() {
    if (prometheusClient == null) {
      log.debug("Prometheus client not configured, skipping analysis processing")
      return
    }

    val now = Instant.now()
    val pending = analysisRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
      ImpactAnalysisStatus.PENDING,
      now
    )

    if (pending.isEmpty()) {
      return
    }

    log.info("Found {} pending version impact analyses to process", pending.size)

    pending.take(properties.maxConcurrentAnalyses).forEach { analysis ->
      executor.submit {
        try {
          executeAnalysis(analysis.id!!)
        } catch (e: Exception) {
          log.error("Error processing analysis {}", analysis.id, e)
        }
      }
    }
  }

  @Transactional
  fun executeAnalysis(analysisId: Long) {
    val analysis = analysisRepository.findById(analysisId).orElse(null)
    if (analysis == null || analysis.status != ImpactAnalysisStatus.PENDING) {
      return
    }

    log.info("Executing version impact analysis {}", analysisId)

    analysis.status = ImpactAnalysisStatus.RUNNING
    analysis.startedAt = Instant.now()
    analysisRepository.save(analysis)

    try {
      val versionHistory = analysis.versionHistory
      val workloadInstance = versionHistory.workloadInstance

      val completedAt = versionHistory.deploymentCompletedAt
      if (completedAt == null) {
        markSkipped(analysis, "Deployment completed timestamp not available")
        return
      }

      val windowDuration = Duration.ofMinutes(properties.windowMinutes)

      val referenceTime = versionHistory.deploymentStartedAt ?: completedAt
      val preStart = referenceTime.minus(windowDuration)
      val preEnd = referenceTime

      val postStart = analysis.scheduledAt
      val postEnd = postStart.plus(windowDuration)

      if (postEnd.isAfter(Instant.now())) {
        log.debug("Post-deployment window not yet complete for analysis {}", analysisId)
        analysis.status = ImpactAnalysisStatus.PENDING
        analysisRepository.save(analysis)
        return
      }

      analysis.preDeploymentWindowStart = preStart
      analysis.preDeploymentWindowEnd = preEnd
      analysis.postDeploymentWindowStart = postStart
      analysis.postDeploymentWindowEnd = postEnd

      val queryContext = QueryContext(
        cluster = workloadInstance.cluster.name,
        namespace = workloadInstance.namespace,
        workload = workloadInstance.workload.name ?: "",
        workloadType = workloadInstance.workload.kind?.lowercase() ?: "",
        window = "${properties.windowMinutes}m"
      )

      val metricsConfig = properties.metrics
      val cpuResult = analyzeMetric("cpu", metricsConfig.cpu, queryContext, preStart, preEnd, postStart, postEnd)
      val memoryResult = analyzeMetric("memory", metricsConfig.memory, queryContext, preStart, preEnd, postStart, postEnd)
      val restartsResult = analyzeMetric("restarts", metricsConfig.restarts, queryContext, preStart, preEnd, postStart, postEnd)
      val errorRateResult = analyzeMetric("errorRate", metricsConfig.errorRate, queryContext, preStart, preEnd, postStart, postEnd)
      val latencyResult = analyzeMetric("latencyP99", metricsConfig.latencyP99, queryContext, preStart, preEnd, postStart, postEnd)

      analysis.metrics = ImpactAnalysisMetrics(
        cpu = cpuResult,
        memory = memoryResult,
        restarts = restartsResult,
        errorRate = errorRateResult,
        latencyP99 = latencyResult
      )

      val allResults = listOfNotNull(cpuResult, memoryResult, restartsResult, errorRateResult, latencyResult)
      analysis.result = when {
        allResults.isEmpty() -> ImpactAnalysisResult.UNKNOWN
        allResults.any { it.exceeded } -> ImpactAnalysisResult.DEGRADED
        else -> ImpactAnalysisResult.HEALTHY
      }

      analysis.status = ImpactAnalysisStatus.COMPLETED
      analysis.completedAt = Instant.now()

      log.info("Analysis {} completed with result: {}", analysisId, analysis.result)

    } catch (e: Exception) {
      log.error("Analysis {} failed", analysisId, e)
      analysis.status = ImpactAnalysisStatus.FAILED
      analysis.errorMessage = e.message
      analysis.completedAt = Instant.now()
    }

    analysisRepository.save(analysis)
  }

  private fun analyzeMetric(
    metricName: String,
    config: MetricConfig,
    context: QueryContext,
    preStart: Instant,
    preEnd: Instant,
    postStart: Instant,
    postEnd: Instant
  ): MetricResult? {
    if (!config.enabled || config.query.isBlank()) {
      return null
    }

    if (prometheusClient == null) return null

    val query = buildQuery(config.query, context)

    val preResult = prometheusClient.queryRange(query, preStart, preEnd)
    val preValue = preResult?.average()

    val postResult = prometheusClient.queryRange(query, postStart, postEnd)
    val postValue = postResult?.average()

    if (preValue == null && postValue == null) {
      log.warn("No metrics data available for {} on workload {}", metricName, context.workload)
      return null
    }

    val changeAbsolute = if (preValue != null && postValue != null) {
      postValue - preValue
    } else {
      null
    }

    val changePercent = if (preValue != null && postValue != null && preValue > 0) {
      ((postValue - preValue) / preValue) * 100
    } else {
      null
    }

    val exceeded = evaluateThreshold(config, changePercent, changeAbsolute, postValue)
    val reason = buildExceededReason(config, changePercent, changeAbsolute, postValue, exceeded)

    return MetricResult(
      preDeploymentValue = preValue,
      postDeploymentValue = postValue,
      changePercent = changePercent,
      changeAbsolute = changeAbsolute,
      thresholdPercent = config.thresholdPercent,
      thresholdAbsolute = config.thresholdAbsolute,
      exceeded = exceeded,
      reason = reason
    )
  }

  private fun evaluateThreshold(
    config: MetricConfig,
    changePercent: Double?,
    changeAbsolute: Double?,
    postValue: Double?
  ): Boolean {
    if (config.thresholdAbsolute != null) {
      val absoluteValue = changeAbsolute ?: postValue ?: 0.0
      if (absoluteValue >= config.thresholdAbsolute) {
        return true
      }
    }

    if (changePercent != null && changePercent > config.thresholdPercent) {
      return true
    }

    return false
  }

  private fun buildExceededReason(
    config: MetricConfig,
    changePercent: Double?,
    changeAbsolute: Double?,
    postValue: Double?,
    exceeded: Boolean
  ): String? {
    if (!exceeded) return null

    val reasons = mutableListOf<String>()

    if (config.thresholdAbsolute != null) {
      val absoluteValue = changeAbsolute ?: postValue ?: 0.0
      if (absoluteValue >= config.thresholdAbsolute) {
        reasons.add("absolute value %.2f >= threshold %.2f".format(absoluteValue, config.thresholdAbsolute))
      }
    }

    if (changePercent != null && changePercent > config.thresholdPercent) {
      reasons.add("change %.1f%% > threshold %.1f%%".format(changePercent, config.thresholdPercent))
    }

    return reasons.joinToString("; ")
  }

  private fun buildQuery(template: String, context: QueryContext): String {
    return template
      .replace("{{cluster}}", context.cluster)
      .replace("{{namespace}}", context.namespace)
      .replace("{{workload}}", context.workload)
      .replace("{{workloadType}}", context.workloadType)
      .replace("{{window}}", context.window)
  }

  private fun markSkipped(analysis: VersionImpactAnalysisEntity, reason: String) {
    analysis.status = ImpactAnalysisStatus.SKIPPED
    analysis.errorMessage = reason
    analysis.completedAt = Instant.now()
    analysisRepository.save(analysis)
  }

  private data class QueryContext(
    val cluster: String,
    val namespace: String,
    val workload: String,
    val workloadType: String,
    val window: String
  )
}
