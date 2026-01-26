package sh.apptrail.controlplane.application.service

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.gitprovider.GitProviderRegistry
import sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionHistoryRepository

/**
 * Service for fetching release information asynchronously.
 * Runs as a scheduled background job processing version history entries without releases.
 */
@Service
@ConditionalOnProperty(prefix = "app.release-fetch", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class ReleaseFetchService(
  private val versionHistoryRepository: VersionHistoryRepository,
  private val providerRegistry: GitProviderRegistry,
  private val releaseService: ReleaseService,
) {
  private val log = LoggerFactory.getLogger(ReleaseFetchService::class.java)

  /**
   * Attempts to link a version history entry to an existing release, or queues it for fetching.
   * Called after a new version is detected.
   */
  @Transactional
  fun queueReleaseFetch(versionHistoryId: Long) {
    val versionHistory = versionHistoryRepository.findById(versionHistoryId).orElse(null) ?: return
    val workload = versionHistory.workloadInstance.workload

    if (workload.repositoryUrl.isNullOrBlank()) {
      log.debug("No repository URL for workload {}, skipping release fetch", workload.name)
      return
    }

    // Try to link to an existing release first
    if (releaseService.findAndLinkRelease(versionHistoryId)) {
      log.debug("Linked version {} to existing release for workload {}", versionHistory.currentVersion, workload.name)
    } else {
      log.debug("Queued version {} for release fetch for workload {}", versionHistory.currentVersion, workload.name)
    }
  }

  /**
   * Scheduled job to process version history entries without releases.
   * Runs every 30 seconds with a 10 second initial delay.
   */
  @Scheduled(fixedDelayString = "\${app.release-fetch.interval-ms:30000}", initialDelay = 10000)
  @Transactional
  fun processPendingFetches() {
    val batchSize = 50
    val pending = versionHistoryRepository.findByReleaseIsNullAndHasRepositoryUrl(PageRequest.of(0, batchSize))

    if (pending.isEmpty()) {
      return
    }

    log.info("Processing {} pending release fetches", pending.size)

    for (entry in pending) {
      try {
        fetchAndStoreRelease(entry)
      } catch (e: Exception) {
        log.error("Failed to fetch release for version {}: {}", entry.currentVersion, e.message)
      }
    }
  }

  private fun fetchAndStoreRelease(entry: VersionHistoryEntity) {
    val workload = entry.workloadInstance.workload
    val repositoryUrl = workload.repositoryUrl

    if (repositoryUrl.isNullOrBlank()) {
      return
    }

    // First, check if a release already exists in our database
    val existingRelease = releaseService.findReleaseWithVersionNormalization(repositoryUrl, entry.currentVersion)
    if (existingRelease != null) {
      entry.release = existingRelease
      versionHistoryRepository.save(entry)
      log.debug("Linked version {} to existing release {} for workload {}",
        entry.currentVersion, existingRelease.tagName, workload.name)
      return
    }

    // No existing release - fetch from provider
    val provider = providerRegistry.findProvider(repositoryUrl)
    if (provider == null) {
      log.warn("No provider found for repository URL: {}", repositoryUrl)
      return
    }

    log.debug("Fetching release from {} for version {}", provider.providerId, entry.currentVersion)

    val releaseInfo = provider.fetchRelease(repositoryUrl, entry.currentVersion)

    if (releaseInfo != null) {
      // Create the release record and link it
      val release = releaseService.upsertRelease(repositoryUrl, releaseInfo)
      entry.release = release
      versionHistoryRepository.save(entry)
      log.info("Successfully fetched and linked release {} for workload {}", release.tagName, workload.name)
    } else {
      log.debug("No release found for version {} of workload {}", entry.currentVersion, workload.name)
    }
  }
}
