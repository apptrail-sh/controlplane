package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import sh.apptrail.controlplane.infrastructure.persistence.entity.base.BaseEntity
import java.time.Instant

@Entity
@Table(name = "pods")
class PodEntity : BaseEntity() {

  @ManyToOne
  @JoinColumn(name = "cluster_id", nullable = false)
  lateinit var cluster: ClusterEntity

  @ManyToOne
  @JoinColumn(name = "workload_instance_id")
  var workloadInstance: WorkloadInstanceEntity? = null

  @ManyToOne
  @JoinColumn(name = "node_id")
  var node: NodeEntity? = null

  @field:Column(nullable = false)
  var namespace: String = ""

  @field:Column(nullable = false)
  var name: String = ""

  @field:Column(nullable = false)
  var uid: String = ""

  @field:Column(columnDefinition = "jsonb")
  @field:JdbcTypeCode(SqlTypes.JSON)
  var labels: Map<String, String>? = null

  @field:Column(columnDefinition = "jsonb")
  @field:JdbcTypeCode(SqlTypes.JSON)
  var status: PodStatus? = null

  @field:Column(name = "first_seen_at", nullable = false)
  var firstSeenAt: Instant = Instant.now()

  @field:Column(name = "last_updated_at", nullable = false)
  var lastUpdatedAt: Instant = Instant.now()

  @field:Column(name = "deleted_at")
  var deletedAt: Instant? = null
}

data class PodStatus(
  val phase: String? = null,
  val conditions: List<PodCondition>? = null,
  val podIP: String? = null,
  val startTime: Instant? = null,
  val containerStatuses: List<ContainerStatus>? = null,
  val initContainerStatuses: List<ContainerStatus>? = null
)

data class PodCondition(
  val type: String,
  val status: String,
  val reason: String? = null,
  val message: String? = null
)

data class ContainerStatus(
  val name: String,
  val image: String? = null,
  val ready: Boolean = false,
  val restartCount: Int = 0,
  val state: String? = null,
  val reason: String? = null,
  val message: String? = null
)
