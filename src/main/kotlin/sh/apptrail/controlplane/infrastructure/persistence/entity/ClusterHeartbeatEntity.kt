package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.*
import sh.apptrail.controlplane.infrastructure.persistence.entity.base.BaseEntity
import java.time.Instant

@Entity
@Table(name = "cluster_heartbeats")
class ClusterHeartbeatEntity : BaseEntity() {

  @ManyToOne
  @JoinColumn(name = "cluster_id", nullable = false)
  lateinit var cluster: ClusterEntity

  @field:Column(name = "agent_version")
  var agentVersion: String? = null

  @field:Column(name = "received_at", nullable = false)
  var receivedAt: Instant = Instant.now()

  @field:Column(name = "node_count", nullable = false)
  var nodeCount: Int = 0

  @field:Column(name = "pod_count", nullable = false)
  var podCount: Int = 0
}
