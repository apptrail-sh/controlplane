package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import sh.apptrail.controlplane.infrastructure.persistence.entity.UserEntity

interface UserRepository : JpaRepository<UserEntity, Long> {
  fun findByProviderAndProviderSub(provider: String, providerSub: String): UserEntity?
  fun findByEmail(email: String): UserEntity?
}
