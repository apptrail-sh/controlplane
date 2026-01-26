package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import sh.apptrail.controlplane.infrastructure.persistence.entity.ReleaseEntity

interface ReleaseRepository : JpaRepository<ReleaseEntity, Long> {
  fun findByRepositoryUrlAndTagName(repositoryUrl: String, tagName: String): ReleaseEntity?
  fun findByRepositoryUrl(repositoryUrl: String): List<ReleaseEntity>
}
