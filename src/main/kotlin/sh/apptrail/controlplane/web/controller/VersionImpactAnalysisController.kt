package sh.apptrail.controlplane.web.controller

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sh.apptrail.controlplane.infrastructure.persistence.entity.ImpactAnalysisResult
import sh.apptrail.controlplane.infrastructure.persistence.entity.ImpactAnalysisStatus
import sh.apptrail.controlplane.infrastructure.persistence.entity.VersionImpactAnalysisEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionImpactAnalysisRepository
import java.time.Instant

@RestController
@RequestMapping("/api/v1/impact-analyses")
class VersionImpactAnalysisController(
  private val analysisRepository: VersionImpactAnalysisRepository
) {

  @GetMapping
  fun listAnalyses(
    @RequestParam(required = false) status: ImpactAnalysisStatus?,
    @RequestParam(required = false) result: ImpactAnalysisResult?,
    @RequestParam(required = false) workloadId: Long?,
    @RequestParam(required = false) workloadInstanceId: Long?,
    @RequestParam(required = false, defaultValue = "50") limit: Int
  ): List<ImpactAnalysisResponse> {
    val analyses = when {
      workloadInstanceId != null -> analysisRepository.findByWorkloadInstanceIdOrderByCreatedAtDesc(workloadInstanceId)
      workloadId != null -> analysisRepository.findByWorkloadIdOrderByCreatedAtDesc(workloadId)
      else -> analysisRepository.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).content
    }

    return analyses
      .filter { status == null || it.status == status }
      .filter { result == null || it.result == result }
      .take(limit)
      .map { it.toResponse() }
  }

  @GetMapping("/{id}")
  fun getAnalysis(@PathVariable id: Long): ResponseEntity<ImpactAnalysisDetailResponse> {
    val analysis = analysisRepository.findById(id).orElse(null)
      ?: return ResponseEntity.notFound().build()

    return ResponseEntity.ok(analysis.toDetailResponse())
  }

  @GetMapping("/deployment/{versionHistoryId}")
  fun getAnalysisByDeployment(@PathVariable versionHistoryId: Long): ResponseEntity<ImpactAnalysisDetailResponse> {
    val analysis = analysisRepository.findByVersionHistoryId(versionHistoryId)
      ?: return ResponseEntity.notFound().build()

    return ResponseEntity.ok(analysis.toDetailResponse())
  }
}

private fun VersionImpactAnalysisEntity.toResponse() = ImpactAnalysisResponse(
  id = id ?: 0,
  versionHistoryId = versionHistory.id ?: 0,
  workloadInstanceId = versionHistory.workloadInstance.id ?: 0,
  workloadName = versionHistory.workloadInstance.workload.name ?: "",
  cluster = versionHistory.workloadInstance.cluster.name,
  namespace = versionHistory.workloadInstance.namespace,
  version = versionHistory.currentVersion,
  status = status.name,
  result = result?.name,
  scheduledAt = scheduledAt,
  completedAt = completedAt,
  createdAt = createdAt
)

private fun VersionImpactAnalysisEntity.toDetailResponse() = ImpactAnalysisDetailResponse(
  id = id ?: 0,
  versionHistoryId = versionHistory.id ?: 0,
  workloadInstanceId = versionHistory.workloadInstance.id ?: 0,
  workloadName = versionHistory.workloadInstance.workload.name ?: "",
  cluster = versionHistory.workloadInstance.cluster.name,
  namespace = versionHistory.workloadInstance.namespace,
  version = versionHistory.currentVersion,
  previousVersion = versionHistory.previousVersion,
  status = status.name,
  result = result?.name,
  scheduledAt = scheduledAt,
  startedAt = startedAt,
  completedAt = completedAt,
  preDeploymentWindow = TimeWindowResponse(preDeploymentWindowStart, preDeploymentWindowEnd),
  postDeploymentWindow = TimeWindowResponse(postDeploymentWindowStart, postDeploymentWindowEnd),
  metrics = metrics?.let { MetricsResponse(it) },
  errorMessage = errorMessage,
  createdAt = createdAt
)

data class ImpactAnalysisResponse(
  val id: Long,
  val versionHistoryId: Long,
  val workloadInstanceId: Long,
  val workloadName: String,
  val cluster: String,
  val namespace: String,
  val version: String,
  val status: String,
  val result: String?,
  val scheduledAt: Instant,
  val completedAt: Instant?,
  val createdAt: Instant?
)

data class ImpactAnalysisDetailResponse(
  val id: Long,
  val versionHistoryId: Long,
  val workloadInstanceId: Long,
  val workloadName: String,
  val cluster: String,
  val namespace: String,
  val version: String,
  val previousVersion: String?,
  val status: String,
  val result: String?,
  val scheduledAt: Instant,
  val startedAt: Instant?,
  val completedAt: Instant?,
  val preDeploymentWindow: TimeWindowResponse,
  val postDeploymentWindow: TimeWindowResponse,
  val metrics: MetricsResponse?,
  val errorMessage: String?,
  val createdAt: Instant?
)

data class TimeWindowResponse(
  val start: Instant?,
  val end: Instant?
)

data class MetricsResponse(
  val cpu: MetricResultResponse?,
  val memory: MetricResultResponse?,
  val restarts: MetricResultResponse?,
  val errorRate: MetricResultResponse?,
  val latencyP99: MetricResultResponse?
) {
  constructor(metrics: sh.apptrail.controlplane.infrastructure.persistence.entity.ImpactAnalysisMetrics) : this(
    cpu = metrics.cpu?.let { MetricResultResponse(it) },
    memory = metrics.memory?.let { MetricResultResponse(it) },
    restarts = metrics.restarts?.let { MetricResultResponse(it) },
    errorRate = metrics.errorRate?.let { MetricResultResponse(it) },
    latencyP99 = metrics.latencyP99?.let { MetricResultResponse(it) }
  )
}

data class MetricResultResponse(
  val preDeploymentValue: Double?,
  val postDeploymentValue: Double?,
  val changePercent: Double?,
  val changeAbsolute: Double?,
  val thresholdPercent: Double?,
  val thresholdAbsolute: Double?,
  val exceeded: Boolean,
  val reason: String?
) {
  constructor(metric: sh.apptrail.controlplane.infrastructure.persistence.entity.MetricResult) : this(
    preDeploymentValue = metric.preDeploymentValue,
    postDeploymentValue = metric.postDeploymentValue,
    changePercent = metric.changePercent,
    changeAbsolute = metric.changeAbsolute,
    thresholdPercent = metric.thresholdPercent,
    thresholdAbsolute = metric.thresholdAbsolute,
    exceeded = metric.exceeded,
    reason = metric.reason
  )
}
