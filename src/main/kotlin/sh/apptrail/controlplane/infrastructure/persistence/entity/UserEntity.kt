package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import sh.apptrail.controlplane.infrastructure.persistence.entity.base.BaseEntity
import java.time.Instant

@Entity
@Table(name = "users")
class UserEntity : BaseEntity() {

  @field:Column(nullable = false, unique = true)
  var email: String = ""

  var name: String? = null

  @field:Column(name = "picture_url")
  var pictureUrl: String? = null

  @field:Column(nullable = false)
  var provider: String = "google"

  @field:Column(name = "provider_sub", nullable = false)
  var providerSub: String = ""

  @field:Column(name = "last_login_at")
  var lastLoginAt: Instant? = null

  @field:Column(name = "token_version", nullable = false)
  var tokenVersion: Int = 0
}
