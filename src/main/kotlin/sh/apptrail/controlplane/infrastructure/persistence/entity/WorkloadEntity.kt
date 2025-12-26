package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.Entity
import jakarta.persistence.Table
import sh.apptrail.controlplane.infrastructure.persistence.entity.base.BaseEntity

@Entity
@Table(name = "workloads")
class WorkloadEntity : BaseEntity() {

  var group: String? = null
  var kind: String? = null
  var name: String? = null
  var team: String? = null

}
