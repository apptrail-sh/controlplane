package sh.apptrail.controlplane.application.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import sh.apptrail.controlplane.infrastructure.gitprovider.GitProviderRegistry
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionHistoryRepository

/**
 * Processes individual version history entries for release fetching.
 * Each entry is processed in its own isolated transaction using REQUIRES_NEW.
 *
 * This isolation ensures that:
 * - A failure in one entry doesn't affect others
 * - Successful entries are committed immediately
 * - Failed entries are rolled back independently
 * - The Hibernate session remains uncorrupted for subsequent operations
 */
@Component
class ReleaseFetchProcessor(
  private val versionHistoryRepository: VersionHistoryRepository,
  private val providerRegistry: GitProviderRegistry,
  private val releaseService: ReleaseService,
  private val releaseFetchAttemptService: ReleaseFetchAttemptService,
) {
  private val log = LoggerFactory.getLogger(ReleaseFetchProcessor::class.java)

  /**
   * Process a single version history entry in its own transaction.
   * Uses REQUIRES_NEW to ensure complete isolation from the caller's transaction.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun processEntry(entryId: Long) {
    val entry = versionHistoryRepository.findById(entryId).orElse(null)
    if (entry == null) {
      log.debug("Entry {} no longer exists, skipping", entryId)
      return
    }

    // Check if already linked (could have been linked by another instance)
    if (entry.release != null) {
      log.debug("Entry {} already has a release, skipping", entryId)
      return
    }

    val workload = entry.workloadInstance.workload
    val repository = workload.repository

    if (repository == null) {
      log.debug("Skipping entry {} - no repository for workload {}", entry.id, workload.name)
      return
    }

    // Check if a release already exists in our database
    val existingRelease = releaseService.findReleaseWithVersionNormalization(repository, entry.currentVersion)
    if (existingRelease != null) {
      entry.release = existingRelease
      versionHistoryRepository.save(entry)
      log.info("Linked version history {} (version {}) to existing release {} for workload {}",
        entry.id, entry.currentVersion, existingRelease.tagName, workload.name)
      return
    }

    // No existing release - fetch from provider
    val provider = providerRegistry.findProvider(repository.url)
    if (provider == null) {
      log.warn("No provider found for repository URL: {}", repository.url)
      return
    }

    log.info("Fetching release from {} for version {} (workload: {})",
      provider.providerId, entry.currentVersion, workload.name)

    val releaseInfo = provider.fetchRelease(repository.url, entry.currentVersion)

    if (releaseInfo != null) {
      val release = releaseService.upsertRelease(repository, releaseInfo)
      releaseService.linkPendingVersionHistories(repository, release)
      releaseFetchAttemptService.clearAttempt(repository, entry.currentVersion)
      log.info("Successfully fetched release {} and linked pending version histories", release.tagName)
    } else {
      releaseFetchAttemptService.recordFailedAttempt(repository, entry.currentVersion)
      log.info("No release found for {} version {} (will retry after 24h)",
        repository.url, entry.currentVersion)
    }
  }
}
