package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import sh.apptrail.controlplane.infrastructure.persistence.entity.base.BaseEntity

@Entity
@Table(name = "workloads")
class WorkloadEntity : BaseEntity() {

  var kind: String? = null
  var name: String? = null
  var team: String? = null

  @field:Column(name = "part_of")
  var partOf: String? = null

  @field:Column(name = "repository_url")
  var repositoryUrl: String? = null
  var description: String? = null

}
