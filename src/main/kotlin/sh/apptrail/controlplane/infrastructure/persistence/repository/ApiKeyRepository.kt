package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import sh.apptrail.controlplane.infrastructure.persistence.entity.ApiKeyEntity

interface ApiKeyRepository : JpaRepository<ApiKeyEntity, Long> {
  fun findByKeyHash(keyHash: String): ApiKeyEntity?
}
