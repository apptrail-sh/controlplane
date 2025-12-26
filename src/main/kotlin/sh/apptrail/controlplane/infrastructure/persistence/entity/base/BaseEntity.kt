package sh.apptrail.controlplane.infrastructure.persistence.entity.base

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import java.time.Instant


@Access(AccessType.FIELD) // makes it unambiguous that annotations apply to fields
@MappedSuperclass
class BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null

  @field:CreatedDate
  @field:Column(name = "created_at", nullable = false)
  var createdAt: Instant? = null

  @field:LastModifiedDate
  @field:Column(name = "updated_at", nullable = false)
  var updatedAt: Instant? = null

  @PrePersist
  fun onCreate() {
    val now = Instant.now()
    if (createdAt == null) {
      createdAt = now
    }
    updatedAt = now
  }

  @PreUpdate
  fun onUpdate() {
    updatedAt = Instant.now()
  }
}
