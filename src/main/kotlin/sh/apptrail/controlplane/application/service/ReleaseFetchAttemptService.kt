package sh.apptrail.controlplane.application.service

import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.persistence.entity.ReleaseFetchAttemptEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.RepositoryEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.ReleaseFetchAttemptRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class ReleaseFetchAttemptService(
  private val attemptRepository: ReleaseFetchAttemptRepository,
) {
  private val log = LoggerFactory.getLogger(ReleaseFetchAttemptService::class.java)
  private val retryAfterHours = 24L

  /**
   * Normalizes a version string by removing the 'v' prefix if present.
   * This ensures that "1.2.3" and "v1.2.3" are treated as the same version.
   */
  private fun normalizeVersion(version: String): String {
    return version.removePrefix("v").removePrefix("V")
  }

  /**
   * Checks if we recently attempted to fetch this version and failed.
   */
  fun wasRecentlyAttempted(repository: RepositoryEntity, version: String): Boolean {
    val normalized = normalizeVersion(version)
    val cutoff = Instant.now().minus(retryAfterHours, ChronoUnit.HOURS)
    return attemptRepository.existsByRepositoryAndVersionAndAttemptedAtAfter(
      repository, normalized, cutoff
    )
  }

  /**
   * Records a failed fetch attempt.
   */
  @Transactional
  fun recordFailedAttempt(repository: RepositoryEntity, version: String) {
    val normalized = normalizeVersion(version)
    val existing = attemptRepository.findByRepositoryAndVersion(repository, normalized)
    if (existing != null) {
      existing.attemptedAt = Instant.now()
      attemptRepository.save(existing)
      log.debug("Updated failed fetch attempt for {} version {}", repository.url, normalized)
    } else {
      attemptRepository.save(
        ReleaseFetchAttemptEntity(
          repository = repository,
          version = normalized,
          attemptedAt = Instant.now(),
        )
      )
      log.debug("Recorded failed fetch attempt for {} version {}", repository.url, normalized)
    }
  }

  /**
   * Clears failed attempt record (called when a release is found via webhook).
   */
  @Transactional
  fun clearAttempt(repository: RepositoryEntity, version: String) {
    val normalized = normalizeVersion(version)
    attemptRepository.deleteByRepositoryAndVersion(repository, normalized)
    log.debug("Cleared failed fetch attempt for {} version {}", repository.url, normalized)
  }
}
