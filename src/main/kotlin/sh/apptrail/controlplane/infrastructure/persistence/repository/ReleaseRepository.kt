package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import sh.apptrail.controlplane.infrastructure.persistence.entity.ReleaseEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.RepositoryEntity

interface ReleaseRepository : JpaRepository<ReleaseEntity, Long> {
  fun findByRepositoryAndTagName(repository: RepositoryEntity, tagName: String): ReleaseEntity?
  fun findByRepository(repository: RepositoryEntity): List<ReleaseEntity>
}
