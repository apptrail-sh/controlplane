package sh.apptrail.controlplane.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import sh.apptrail.controlplane.entity.base.BaseEntity

@Entity
class ClusterEntity : BaseEntity() {

  @field:Column(nullable = false, unique = true)
  var name: String = ""

}
