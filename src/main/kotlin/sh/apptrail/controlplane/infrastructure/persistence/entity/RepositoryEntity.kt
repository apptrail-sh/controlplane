package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "repositories")
class RepositoryEntity(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null,

  @Column(name = "url", nullable = false, unique = true)
  var url: String,

  @Column(name = "provider", nullable = false)
  var provider: String,

  @Column(name = "owner")
  var owner: String? = null,

  @Column(name = "name")
  var name: String? = null,

  @Column(name = "created_at", insertable = false, updatable = false)
  var createdAt: Instant? = null,

  @Column(name = "updated_at", insertable = false, updatable = false)
  var updatedAt: Instant? = null,
)
