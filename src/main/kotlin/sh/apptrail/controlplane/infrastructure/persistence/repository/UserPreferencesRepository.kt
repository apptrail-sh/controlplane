package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import sh.apptrail.controlplane.infrastructure.persistence.entity.UserPreferencesEntity

interface UserPreferencesRepository : JpaRepository<UserPreferencesEntity, Long> {
  fun findByUserId(userId: Long): UserPreferencesEntity?
}
