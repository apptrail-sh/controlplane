package sh.apptrail.controlplane.infrastructure.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import sh.apptrail.controlplane.infrastructure.gitprovider.model.ReleaseAuthor
import java.time.Instant

@Entity
@Table(name = "releases")
class ReleaseEntity(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null,

  @Column(name = "repository_url", nullable = false)
  var repositoryUrl: String,

  @Column(name = "tag_name", nullable = false)
  var tagName: String,

  @Column(name = "name")
  var name: String? = null,

  @Column(name = "body", columnDefinition = "TEXT")
  var body: String? = null,

  @Column(name = "html_url")
  var htmlUrl: String? = null,

  @Column(name = "published_at")
  var publishedAt: Instant? = null,

  @Column(name = "is_draft", nullable = false)
  var isDraft: Boolean = false,

  @Column(name = "is_prerelease", nullable = false)
  var isPrerelease: Boolean = false,

  @Column(name = "authors", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  var authors: List<ReleaseAuthor>? = null,

  @Column(name = "provider", nullable = false)
  var provider: String,

  @Column(name = "fetched_at", nullable = false)
  var fetchedAt: Instant,

  @Column(name = "created_at", insertable = false, updatable = false)
  var createdAt: Instant? = null,
)
