package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import sh.apptrail.controlplane.infrastructure.persistence.entity.ReleaseFetchAttemptEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.RepositoryEntity
import java.time.Instant

interface ReleaseFetchAttemptRepository : JpaRepository<ReleaseFetchAttemptEntity, Long> {
  fun findByRepositoryAndVersion(repository: RepositoryEntity, version: String): ReleaseFetchAttemptEntity?

  fun existsByRepositoryAndVersionAndAttemptedAtAfter(
    repository: RepositoryEntity,
    version: String,
    cutoff: Instant
  ): Boolean

  fun deleteByRepositoryAndVersion(repository: RepositoryEntity, version: String)
}
