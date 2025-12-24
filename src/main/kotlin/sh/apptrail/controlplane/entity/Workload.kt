package sh.apptrail.controlplane.entity

import jakarta.persistence.Entity
import sh.apptrail.controlplane.entity.base.BaseEntity

@Entity
class Workload : BaseEntity() {

  var group: String? = null
  var kind: String? = null
  var name: String? = null
  var team: String? = null

}
