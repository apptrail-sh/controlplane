package sh.apptrail.controlplane.infrastructure.gitprovider.model

import java.time.Instant

data class ReleaseInfo(
  val provider: String,
  val tagName: String,
  val name: String?,
  val body: String?,
  val publishedAt: Instant?,
  val htmlUrl: String?,
  val authors: List<ReleaseAuthor>,
  val isDraft: Boolean = false,
  val isPrerelease: Boolean = false,
)

data class ReleaseAuthor(
  val login: String,
  val avatarUrl: String?,
)
