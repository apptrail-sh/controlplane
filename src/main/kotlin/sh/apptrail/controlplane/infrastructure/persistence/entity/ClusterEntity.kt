package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import sh.apptrail.controlplane.infrastructure.persistence.entity.base.BaseEntity

@Entity
@Table(name = "clusters")
class ClusterEntity : BaseEntity() {

  @field:Column(nullable = false, unique = true)
  var name: String = ""

}
