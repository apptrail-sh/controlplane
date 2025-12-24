package sh.apptrail.controlplane.entity.base

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import java.time.ZonedDateTime


@Access(AccessType.FIELD) // makes it unambiguous that annotations apply to fields
@MappedSuperclass
class BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  var id: Long? = null

  @field:CreatedDate
  var createdAt: ZonedDateTime? = null

  @field:LastModifiedDate
  var updatedAt: ZonedDateTime? = null

}
