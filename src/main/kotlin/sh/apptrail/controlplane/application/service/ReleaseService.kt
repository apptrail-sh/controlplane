package sh.apptrail.controlplane.application.service

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.gitprovider.model.ReleaseInfo
import sh.apptrail.controlplane.infrastructure.persistence.entity.ReleaseEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.ReleaseRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionHistoryRepository
import java.time.Instant

@Service
class ReleaseService(
  private val releaseRepository: ReleaseRepository,
  private val versionHistoryRepository: VersionHistoryRepository,
) {
  private val log = LoggerFactory.getLogger(ReleaseService::class.java)

  /**
   * Creates or updates a release in the database.
   * @return The created or updated release entity
   */
  @Transactional
  fun upsertRelease(repositoryUrl: String, releaseInfo: ReleaseInfo): ReleaseEntity {
    val normalizedUrl = normalizeRepositoryUrl(repositoryUrl)
    val existing = releaseRepository.findByRepositoryUrlAndTagName(normalizedUrl, releaseInfo.tagName)

    return if (existing != null) {
      log.debug("Updating existing release {} for {}", releaseInfo.tagName, normalizedUrl)
      existing.apply {
        name = releaseInfo.name
        body = releaseInfo.body
        htmlUrl = releaseInfo.htmlUrl
        publishedAt = releaseInfo.publishedAt
        isDraft = releaseInfo.isDraft
        isPrerelease = releaseInfo.isPrerelease
        authors = releaseInfo.authors
        provider = releaseInfo.provider
        fetchedAt = Instant.now()
      }
      releaseRepository.save(existing)
    } else {
      log.info("Creating new release {} for {}", releaseInfo.tagName, normalizedUrl)
      val release = ReleaseEntity(
        repositoryUrl = normalizedUrl,
        tagName = releaseInfo.tagName,
        name = releaseInfo.name,
        body = releaseInfo.body,
        htmlUrl = releaseInfo.htmlUrl,
        publishedAt = releaseInfo.publishedAt,
        isDraft = releaseInfo.isDraft,
        isPrerelease = releaseInfo.isPrerelease,
        authors = releaseInfo.authors,
        provider = releaseInfo.provider,
        fetchedAt = Instant.now(),
      )
      releaseRepository.save(release)
    }
  }

  /**
   * Finds a release by exact repository URL and tag name.
   */
  fun findRelease(repositoryUrl: String, tagName: String): ReleaseEntity? {
    val normalizedUrl = normalizeRepositoryUrl(repositoryUrl)
    return releaseRepository.findByRepositoryUrlAndTagName(normalizedUrl, tagName)
  }

  /**
   * Finds a release by repository URL, trying multiple tag name variants.
   * Attempts: exact version, with 'v' prefix, without 'v' prefix.
   */
  fun findReleaseWithVersionNormalization(repositoryUrl: String, version: String): ReleaseEntity? {
    val normalizedUrl = normalizeRepositoryUrl(repositoryUrl)
    val tagCandidates = listOf(
      version,
      "v$version",
      version.removePrefix("v")
    ).distinct()

    for (tag in tagCandidates) {
      val release = releaseRepository.findByRepositoryUrlAndTagName(normalizedUrl, tag)
      if (release != null) {
        log.debug("Found release for {} with tag variant {}", normalizedUrl, tag)
        return release
      }
    }

    return null
  }

  /**
   * Links a version history entry to a release.
   */
  @Transactional
  fun linkVersionHistoryToRelease(versionHistoryId: Long, releaseId: Long) {
    versionHistoryRepository.findById(versionHistoryId).ifPresent { versionHistory ->
      releaseRepository.findById(releaseId).ifPresent { release ->
        versionHistory.release = release
        versionHistoryRepository.save(versionHistory)
        log.debug("Linked version history {} to release {}", versionHistoryId, release.tagName)
      }
    }
  }

  /**
   * Attempts to find and link a release for a version history entry.
   * @return true if a release was found and linked, false otherwise
   */
  @Transactional
  fun findAndLinkRelease(versionHistoryId: Long): Boolean {
    val versionHistory = versionHistoryRepository.findById(versionHistoryId).orElse(null) ?: return false

    // Already linked
    if (versionHistory.release != null) {
      return true
    }

    val repositoryUrl = versionHistory.workloadInstance.workload.repositoryUrl
    if (repositoryUrl.isNullOrBlank()) {
      return false
    }

    val release = findReleaseWithVersionNormalization(repositoryUrl, versionHistory.currentVersion)
    if (release != null) {
      versionHistory.release = release
      versionHistoryRepository.save(versionHistory)
      log.info("Auto-linked version history {} to existing release {}", versionHistoryId, release.tagName)
      return true
    }

    return false
  }

  /**
   * Links all pending version history entries for a given repository and tag.
   * Called after a new release is created/updated via webhook.
   */
  @Transactional
  fun linkPendingVersionHistories(repositoryUrl: String, release: ReleaseEntity) {
    val normalizedUrl = normalizeRepositoryUrl(repositoryUrl)

    // Find all version history entries without a release that might match this release
    val pending = versionHistoryRepository.findByReleaseIsNullAndWorkloadInstanceWorkloadRepositoryUrl(normalizedUrl)

    val tagVariants = setOf(
      release.tagName,
      release.tagName.removePrefix("v"),
      "v${release.tagName.removePrefix("v")}"
    )

    for (versionHistory in pending) {
      if (tagVariants.contains(versionHistory.currentVersion)) {
        versionHistory.release = release
        versionHistoryRepository.save(versionHistory)
        log.info("Linked pending version history {} to release {}", versionHistory.id, release.tagName)
      }
    }
  }

  private fun normalizeRepositoryUrl(url: String): String {
    return url.removeSuffix(".git").lowercase()
  }
}
