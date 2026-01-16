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
 * Runs as a scheduled background job processing pending release fetches.
 */
@Service
@ConditionalOnProperty(prefix = "app.release-fetch", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class ReleaseFetchService(
  private val versionHistoryRepository: VersionHistoryRepository,
  private val providerRegistry: GitProviderRegistry,
) {
  private val log = LoggerFactory.getLogger(ReleaseFetchService::class.java)

  companion object {
    const val STATUS_PENDING = "pending"
    const val STATUS_FETCHING = "fetching"
    const val STATUS_SUCCESS = "success"
    const val STATUS_NOT_FOUND = "not_found"
    const val STATUS_ERROR = "error"
    const val STATUS_NO_REPO = "no_repo"
    const val STATUS_NO_PROVIDER = "no_provider"
  }

  /**
   * Queues a version history entry for release fetch.
   * Called after a new version is detected.
   */
  @Transactional
  fun queueReleaseFetch(versionHistoryId: Long) {
    versionHistoryRepository.findById(versionHistoryId).ifPresent { entry ->
      val workload = entry.workloadInstance.workload
      if (workload.repositoryUrl.isNullOrBlank()) {
        entry.releaseFetchStatus = STATUS_NO_REPO
        log.debug("No repository URL for workload {}, skipping release fetch", workload.name)
      } else {
        entry.releaseFetchStatus = STATUS_PENDING
        log.debug("Queued release fetch for version {} of workload {}", entry.currentVersion, workload.name)
      }
      versionHistoryRepository.save(entry)
    }
  }

  /**
   * Scheduled job to process pending release fetches.
   * Runs every 30 seconds with a 10 second initial delay.
   */
  @Scheduled(fixedDelayString = "\${app.release-fetch.interval-ms:30000}", initialDelay = 10000)
  @Transactional
  fun processPendingFetches() {
    val batchSize = 50
    val pending = versionHistoryRepository.findByReleaseFetchStatus(STATUS_PENDING, PageRequest.of(0, batchSize))

    if (pending.isEmpty()) {
      return
    }

    log.info("Processing {} pending release fetches", pending.size)

    for (entry in pending) {
      try {
        fetchAndStoreRelease(entry)
      } catch (e: Exception) {
        log.error("Failed to fetch release for version {}: {}", entry.currentVersion, e.message)
        entry.releaseFetchStatus = STATUS_ERROR
        versionHistoryRepository.save(entry)
      }
    }
  }

  private fun fetchAndStoreRelease(entry: VersionHistoryEntity) {
    val workload = entry.workloadInstance.workload
    val repositoryUrl = workload.repositoryUrl

    if (repositoryUrl.isNullOrBlank()) {
      entry.releaseFetchStatus = STATUS_NO_REPO
      versionHistoryRepository.save(entry)
      return
    }

    entry.releaseFetchStatus = STATUS_FETCHING
    versionHistoryRepository.save(entry)

    val provider = providerRegistry.findProvider(repositoryUrl)
    if (provider == null) {
      log.warn("No provider found for repository URL: {}", repositoryUrl)
      entry.releaseFetchStatus = STATUS_NO_PROVIDER
      versionHistoryRepository.save(entry)
      return
    }

    log.debug("Fetching release from {} for version {}", provider.providerId, entry.currentVersion)

    val release = provider.fetchRelease(repositoryUrl, entry.currentVersion)

    if (release != null) {
      entry.releaseInfo = release
      entry.releaseFetchStatus = STATUS_SUCCESS
      log.info("Successfully fetched release {} for workload {}", release.tagName, workload.name)
    } else {
      entry.releaseFetchStatus = STATUS_NOT_FOUND
      log.debug("No release found for version {} of workload {}", entry.currentVersion, workload.name)
    }

    versionHistoryRepository.save(entry)
  }
}
