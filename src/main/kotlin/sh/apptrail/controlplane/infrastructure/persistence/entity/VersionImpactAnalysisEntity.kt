package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import sh.apptrail.controlplane.infrastructure.persistence.entity.base.BaseEntity
import java.time.Instant

enum class ImpactAnalysisStatus {
  PENDING,
  RUNNING,
  COMPLETED,
  FAILED,
  SKIPPED
}

enum class ImpactAnalysisResult {
  HEALTHY,
  DEGRADED,
  UNKNOWN
}

@Entity
@Table(name = "version_impact_analyses")
class VersionImpactAnalysisEntity : BaseEntity() {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "version_history_id", nullable = false)
  lateinit var versionHistory: VersionHistoryEntity

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  var status: ImpactAnalysisStatus = ImpactAnalysisStatus.PENDING

  @Enumerated(EnumType.STRING)
  @Column(name = "result")
  var result: ImpactAnalysisResult? = null

  @Column(name = "scheduled_at", nullable = false)
  var scheduledAt: Instant = Instant.now()

  @Column(name = "started_at")
  var startedAt: Instant? = null

  @Column(name = "completed_at")
  var completedAt: Instant? = null

  @Column(name = "pre_deployment_window_start")
  var preDeploymentWindowStart: Instant? = null

  @Column(name = "pre_deployment_window_end")
  var preDeploymentWindowEnd: Instant? = null

  @Column(name = "post_deployment_window_start")
  var postDeploymentWindowStart: Instant? = null

  @Column(name = "post_deployment_window_end")
  var postDeploymentWindowEnd: Instant? = null

  @Column(name = "metrics", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  var metrics: ImpactAnalysisMetrics? = null

  @Column(name = "error_message")
  var errorMessage: String? = null
}

data class ImpactAnalysisMetrics(
  val cpu: MetricResult? = null,
  val memory: MetricResult? = null,
  val restarts: MetricResult? = null,
  val errorRate: MetricResult? = null,
  val latencyP99: MetricResult? = null
)

data class MetricResult(
  val preDeploymentValue: Double?,
  val postDeploymentValue: Double?,
  val changePercent: Double?,
  val changeAbsolute: Double?,
  val thresholdPercent: Double?,
  val thresholdAbsolute: Double?,
  val exceeded: Boolean,
  val reason: String? = null
)
