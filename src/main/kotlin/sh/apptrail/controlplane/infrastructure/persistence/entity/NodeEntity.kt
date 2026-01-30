package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import sh.apptrail.controlplane.infrastructure.persistence.entity.base.BaseEntity
import java.time.Instant

@Entity
@Table(name = "nodes")
class NodeEntity : BaseEntity() {

  @ManyToOne
  @JoinColumn(name = "cluster_id", nullable = false)
  lateinit var cluster: ClusterEntity

  @field:Column(nullable = false)
  var name: String = ""

  @field:Column(nullable = false)
  var uid: String = ""

  @field:Column(columnDefinition = "jsonb")
  @field:JdbcTypeCode(SqlTypes.JSON)
  var labels: Map<String, String>? = null

  @field:Column(columnDefinition = "jsonb")
  @field:JdbcTypeCode(SqlTypes.JSON)
  var status: NodeStatus? = null

  @field:Column(name = "first_seen_at", nullable = false)
  var firstSeenAt: Instant = Instant.now()

  @field:Column(name = "last_updated_at", nullable = false)
  var lastUpdatedAt: Instant = Instant.now()

  @field:Column(name = "deleted_at")
  var deletedAt: Instant? = null
}

data class NodeStatus(
  val phase: String? = null,
  val conditions: List<NodeCondition>? = null,
  val capacity: Map<String, String>? = null,
  val allocatable: Map<String, String>? = null,
  val nodeInfo: NodeInfo? = null
)

data class NodeCondition(
  val type: String,
  val status: String,
  val reason: String? = null,
  val message: String? = null
)

data class NodeInfo(
  val kubeletVersion: String? = null,
  val containerRuntimeVersion: String? = null,
  val osImage: String? = null,
  val architecture: String? = null
)
