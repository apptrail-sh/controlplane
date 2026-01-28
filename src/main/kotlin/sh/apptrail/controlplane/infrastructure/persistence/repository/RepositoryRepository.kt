package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import sh.apptrail.controlplane.infrastructure.persistence.entity.RepositoryEntity

interface RepositoryRepository : JpaRepository<RepositoryEntity, Long> {
  fun findByUrl(url: String): RepositoryEntity?
}
