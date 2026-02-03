package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import sh.apptrail.controlplane.infrastructure.persistence.entity.base.BaseEntity
import java.time.Instant

enum class ClusterStatus {
  ONLINE,
  OFFLINE
}

@Entity
@Table(name = "clusters")
class ClusterEntity : BaseEntity() {

  @field:Column(nullable = false, unique = true)
  var name: String = ""

  @field:Column(name = "last_heartbeat_at")
  var lastHeartbeatAt: Instant? = null

  @field:Enumerated(EnumType.STRING)
  @field:Column(nullable = false)
  var status: ClusterStatus = ClusterStatus.ONLINE

}
