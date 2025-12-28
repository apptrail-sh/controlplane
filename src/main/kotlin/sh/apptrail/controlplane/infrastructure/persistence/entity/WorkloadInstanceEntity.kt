package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import sh.apptrail.controlplane.infrastructure.persistence.entity.base.BaseEntity
import java.time.Instant

@Entity
@Table(name = "workload_instances")
class WorkloadInstanceEntity : BaseEntity() {

  @ManyToOne
  @JoinColumn(name = "workload_id", nullable = false)
  lateinit var workload: WorkloadEntity

  @ManyToOne
  @JoinColumn(name = "cluster_id", nullable = false)
  lateinit var cluster: ClusterEntity

  @field:Column(nullable = false)
  var namespace: String = ""

  @field:Column(nullable = false)
  var environment: String = ""

  @field:Column(name = "current_version")
  var currentVersion: String? = null

  @field:Column(columnDefinition = "jsonb")
  @field:JdbcTypeCode(SqlTypes.JSON)
  var labels: Map<String, String>? = null

  @field:Column(name = "first_seen_at", nullable = false)
  var firstSeenAt: Instant? = null

  @field:Column(name = "last_updated_at", nullable = false)
  var lastUpdatedAt: Instant? = null
}
