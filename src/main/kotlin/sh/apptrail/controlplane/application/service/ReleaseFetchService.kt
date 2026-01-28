package sh.apptrail.controlplane.application.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionHistoryRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Service for fetching release information asynchronously.
 * Runs as a scheduled background job processing version history entries without releases.
 */
@Service
@ConditionalOnProperty(prefix = "app.release-fetch", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class ReleaseFetchService(
  private val versionHistoryRepository: VersionHistoryRepository,
  private val releaseService: ReleaseService,
  private val releaseFetchProcessor: ReleaseFetchProcessor,
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

    if (workload.repository == null) {
      log.debug("No repository for workload {}, skipping release fetch", workload.name)
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
   *
   * Uses a native query that excludes entries with recent failed attempts,
   * preventing the scheduler from repeatedly processing the same entries.
   * Uses FOR UPDATE SKIP LOCKED to enable horizontal scaling.
   *
   * Each entry is processed in its own isolated transaction via REQUIRES_NEW,
   * ensuring that a failure in one entry doesn't corrupt the session or affect others.
   */
  @Scheduled(fixedDelayString = "\${app.release-fetch.interval-ms:30000}", initialDelay = 10000)
  fun processPendingFetches() {
    val batchSize = 50
    val cutoff = Instant.now().minus(24, ChronoUnit.HOURS)
    val pending = versionHistoryRepository.findPendingReleaseFetches(cutoff, batchSize)

    if (pending.isEmpty()) {
      return
    }

    log.info("Processing {} pending release fetches", pending.size)

    // Collect IDs before processing (entities will become detached after this transaction)
    val entryIds = pending.mapNotNull { it.id }

    for (entryId in entryIds) {
      try {
        releaseFetchProcessor.processEntry(entryId)
      } catch (e: Exception) {
        log.error("Failed to process entry {}: {}", entryId, e.message)
      }
    }
  }
}
