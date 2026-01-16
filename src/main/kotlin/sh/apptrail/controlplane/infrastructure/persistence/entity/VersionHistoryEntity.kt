package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import sh.apptrail.controlplane.infrastructure.gitprovider.model.ReleaseInfo
import java.time.Instant

@Entity
@Table(name = "version_history")
class VersionHistoryEntity(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null,

  @ManyToOne
  @JoinColumn(name = "workload_instance_id", nullable = false)
  var workloadInstance: WorkloadInstanceEntity,

  @Column(name = "previous_version")
  var previousVersion: String? = null,

  @Column(name = "current_version", nullable = false)
  var currentVersion: String = "",

  @Column(name = "deployment_duration_seconds")
  var deploymentDurationSeconds: Int? = null,

  @Column(name = "deployment_status")
  var deploymentStatus: String? = null,

  @Column(name = "deployment_phase")
  var deploymentPhase: String? = null,

  @Column(name = "deployment_started_at")
  var deploymentStartedAt: Instant? = null,

  @Column(name = "deployment_completed_at")
  var deploymentCompletedAt: Instant? = null,

  @Column(name = "deployment_failed_at")
  var deploymentFailedAt: Instant? = null,

  @Column(name = "detected_at", nullable = false)
  var detectedAt: Instant = Instant.EPOCH,

  @Column(name = "created_at", insertable = false, updatable = false)
  var createdAt: Instant? = null,

  @Column(name = "updated_at", insertable = false, updatable = false)
  var updatedAt: Instant? = null,

  @Column(name = "release_fetch_status")
  var releaseFetchStatus: String? = null,

  @Column(name = "release_info", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  var releaseInfo: ReleaseInfo? = null,
)
