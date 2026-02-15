package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import sh.apptrail.controlplane.infrastructure.persistence.entity.base.BaseEntity
import java.time.Instant

@Entity
@Table(name = "api_keys")
class ApiKeyEntity : BaseEntity() {

  @field:Column(nullable = false)
  var name: String = ""

  @field:Column(name = "key_hash", nullable = false, unique = true)
  var keyHash: String = ""

  @field:Column(name = "key_prefix", nullable = false)
  var keyPrefix: String = ""

  var description: String? = null

  @field:Column(name = "expires_at")
  var expiresAt: Instant? = null

  @field:Column(name = "last_used_at")
  var lastUsedAt: Instant? = null
}
