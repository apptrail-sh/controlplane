package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import sh.apptrail.controlplane.infrastructure.persistence.entity.base.BaseEntity

@Entity
@Table(name = "user_preferences")
class UserPreferencesEntity : BaseEntity() {

  @OneToOne
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  lateinit var user: UserEntity

  @field:Column(columnDefinition = "jsonb", nullable = false)
  @field:JdbcTypeCode(SqlTypes.JSON)
  var preferences: Map<String, Any> = emptyMap()
}
