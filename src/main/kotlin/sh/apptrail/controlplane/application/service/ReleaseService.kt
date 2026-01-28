package sh.apptrail.controlplane.application.service

import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.gitprovider.model.ReleaseInfo
import sh.apptrail.controlplane.infrastructure.persistence.entity.ReleaseEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.RepositoryEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.ReleaseRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionHistoryRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadRepository
import java.time.Instant

@Service
class ReleaseService(
  private val releaseRepository: ReleaseRepository,
  private val versionHistoryRepository: VersionHistoryRepository,
  private val workloadRepository: WorkloadRepository,
) {
  private val log = LoggerFactory.getLogger(ReleaseService::class.java)

  /**
   * Creates or updates a release in the database.
   * @return The created or updated release entity
   */
  @Transactional
  fun upsertRelease(repository: RepositoryEntity, releaseInfo: ReleaseInfo): ReleaseEntity {
    val existing = releaseRepository.findByRepositoryAndTagName(repository, releaseInfo.tagName)

    return if (existing != null) {
      log.debug("Updating existing release {} for {}", releaseInfo.tagName, repository.url)
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
      log.info("Creating new release {} for {}", releaseInfo.tagName, repository.url)
      val release = ReleaseEntity(
        repository = repository,
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
   * Finds a release by exact repository and tag name.
   */
  fun findRelease(repository: RepositoryEntity, tagName: String): ReleaseEntity? {
    return releaseRepository.findByRepositoryAndTagName(repository, tagName)
  }

  /**
   * Finds a release by repository, trying multiple tag name variants.
   * Attempts: exact version, with 'v' prefix, without 'v' prefix.
   */
  fun findReleaseWithVersionNormalization(repository: RepositoryEntity, version: String): ReleaseEntity? {
    val tagCandidates = listOf(
      version,
      "v$version",
      version.removePrefix("v")
    ).distinct()

    for (tag in tagCandidates) {
      val release = releaseRepository.findByRepositoryAndTagName(repository, tag)
      if (release != null) {
        log.debug("Found release for {} with tag variant {}", repository.url, tag)
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

    val repository = versionHistory.workloadInstance.workload.repository ?: return false

    val release = findReleaseWithVersionNormalization(repository, versionHistory.currentVersion)
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
  fun linkPendingVersionHistories(repository: RepositoryEntity, release: ReleaseEntity) {
    // Find all version history entries without a release that might match this release
    val pending = versionHistoryRepository.findByReleaseIsNullAndWorkloadInstanceWorkloadRepository(repository)

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

  /**
   * Backfills releases for all version history entries of a specific workload.
   * Called when a workload's repository URL is set or updated.
   * @return The number of entries that were linked to releases
   */
  @Transactional
  fun backfillReleasesForWorkload(workloadId: Long): Int {
    val workload = workloadRepository.findById(workloadId).orElse(null) ?: return 0
    val repository = workload.repository ?: return 0

    val pending = versionHistoryRepository.findByWorkloadIdAndReleaseIsNull(workloadId)

    var linkedCount = 0
    for (entry in pending) {
      val release = findReleaseWithVersionNormalization(repository, entry.currentVersion)
      if (release != null) {
        entry.release = release
        versionHistoryRepository.save(entry)
        log.info("Backfill: linked version history {} to release {}", entry.id, release.tagName)
        linkedCount++
      }
    }

    if (linkedCount > 0) {
      log.info("Backfilled {} version history entries for workload {}", linkedCount, workload.name)
    }

    return linkedCount
  }

  /**
   * Backfills releases for all version history entries across all workloads.
   * Used for manual admin backfill operations.
   * @return The number of entries that were linked to releases
   */
  @Transactional
  fun backfillAllReleases(): Int {
    val pending = versionHistoryRepository.findAllWithoutReleaseAndHasRepository()

    var linkedCount = 0
    for (entry in pending) {
      val repository = entry.workloadInstance.workload.repository ?: continue
      val release = findReleaseWithVersionNormalization(repository, entry.currentVersion)
      if (release != null) {
        entry.release = release
        versionHistoryRepository.save(entry)
        log.info("Backfill: linked version history {} to release {}", entry.id, release.tagName)
        linkedCount++
      }
    }

    log.info("Backfilled {} version history entries across all workloads", linkedCount)
    return linkedCount
  }
}
