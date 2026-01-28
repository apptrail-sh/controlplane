package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "release_fetch_attempts")
class ReleaseFetchAttemptEntity(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "repository_id", nullable = false)
  var repository: RepositoryEntity,

  @Column(name = "version", nullable = false)
  var version: String,

  @Column(name = "attempted_at", nullable = false)
  var attemptedAt: Instant,
)
