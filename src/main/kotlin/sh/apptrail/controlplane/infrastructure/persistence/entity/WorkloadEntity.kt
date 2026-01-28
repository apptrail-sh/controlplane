package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.*
import sh.apptrail.controlplane.infrastructure.persistence.entity.base.BaseEntity

@Entity
@Table(name = "workloads")
class WorkloadEntity : BaseEntity() {

  var kind: String? = null
  var name: String? = null
  var team: String? = null

  @field:Column(name = "part_of")
  var partOf: String? = null

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "repository_id")
  var repository: RepositoryEntity? = null

  var description: String? = null

  /**
   * Computed property for backwards compatibility.
   * Returns the repository URL from the linked repository entity.
   */
  val repositoryUrl: String?
    get() = repository?.url

}
