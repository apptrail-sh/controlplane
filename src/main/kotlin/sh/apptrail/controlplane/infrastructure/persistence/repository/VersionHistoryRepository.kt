package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity
import java.time.Instant

interface VersionHistoryRepository : JpaRepository<VersionHistoryEntity, Long> {
  fun findByWorkloadInstance_IdOrderByDetectedAtDesc(workloadInstanceId: Long): List<VersionHistoryEntity>
  fun findTopByWorkloadInstance_IdOrderByDetectedAtDesc(workloadInstanceId: Long): VersionHistoryEntity?

  /**
   * Find version history entries without a release that:
   * - Have a workload with a repository
   * - Don't have a recent failed fetch attempt (within cutoff time)
   *
   * Uses native SQL for proper version normalization (stripping 'v' prefix).
   * Uses FOR UPDATE SKIP LOCKED to enable horizontal scaling.
   */
  @Query(
    value = """
      SELECT vh.* FROM version_history vh
      JOIN workload_instances wi ON vh.workload_instance_id = wi.id
      JOIN workloads w ON wi.workload_id = w.id
      WHERE vh.release_id IS NULL
      AND w.repository_id IS NOT NULL
      AND NOT EXISTS (
        SELECT 1 FROM release_fetch_attempts rfa
        WHERE rfa.repository_id = w.repository_id
        AND rfa.version = LOWER(REPLACE(REPLACE(vh.current_version, 'v', ''), 'V', ''))
        AND rfa.attempted_at > :cutoff
      )
      LIMIT :limit
      FOR UPDATE OF vh SKIP LOCKED
    """,
    nativeQuery = true
  )
  fun findPendingReleaseFetches(
    @Param("cutoff") cutoff: Instant,
    @Param("limit") limit: Int
  ): List<VersionHistoryEntity>

  /**
   * Find all version history entries without a release that have a workload with a repository.
   * Used for admin backfill operations (no failed attempt filtering or row locking).
   */
  @Query("""
    SELECT vh FROM VersionHistoryEntity vh
    JOIN vh.workloadInstance wi
    JOIN wi.workload w
    WHERE vh.release IS NULL
    AND w.repository IS NOT NULL
  """)
  fun findAllWithoutReleaseAndHasRepository(): List<VersionHistoryEntity>

  /**
   * Find version history entries without a release for a specific repository.
   */
  @Query("""
    SELECT vh FROM VersionHistoryEntity vh
    JOIN vh.workloadInstance wi
    JOIN wi.workload w
    WHERE vh.release IS NULL
    AND w.repository = :repository
  """)
  fun findByReleaseIsNullAndWorkloadInstanceWorkloadRepository(
    @Param("repository") repository: sh.apptrail.controlplane.infrastructure.persistence.entity.RepositoryEntity
  ): List<VersionHistoryEntity>

  /**
   * Find version history entries without a release for a specific workload ID.
   * Used for backfilling releases when a workload's repository URL is updated.
   */
  @Query("""
    SELECT vh FROM VersionHistoryEntity vh
    JOIN vh.workloadInstance wi
    WHERE vh.release IS NULL
    AND wi.workload.id = :workloadId
  """)
  fun findByWorkloadIdAndReleaseIsNull(
    @Param("workloadId") workloadId: Long
  ): List<VersionHistoryEntity>

  /**
   * Find version history entries for a list of workload instance IDs within a date range.
   * Optimized for workload-level metrics queries.
   */
  @Query("""
    SELECT vh FROM VersionHistoryEntity vh
    WHERE vh.workloadInstance.id IN :instanceIds
    AND vh.detectedAt >= :startDate
    AND vh.detectedAt <= :endDate
    ORDER BY vh.detectedAt DESC
  """)
  fun findByInstanceIdsAndDateRange(
    @Param("instanceIds") instanceIds: List<Long>,
    @Param("startDate") startDate: Instant,
    @Param("endDate") endDate: Instant
  ): List<VersionHistoryEntity>

  /**
   * Find version history entries for workloads belonging to a specific team within a date range.
   * Uses a join through workload_instance to workload to filter by team.
   */
  @Query("""
    SELECT vh FROM VersionHistoryEntity vh
    JOIN vh.workloadInstance wi
    JOIN wi.workload w
    WHERE w.team = :team
    AND vh.detectedAt >= :startDate
    AND vh.detectedAt <= :endDate
    ORDER BY vh.detectedAt DESC
  """)
  fun findByTeamAndDateRange(
    @Param("team") team: String,
    @Param("startDate") startDate: Instant,
    @Param("endDate") endDate: Instant
  ): List<VersionHistoryEntity>

  /**
   * Find version history entries for unassigned workloads (null or empty team) within a date range.
   */
  @Query("""
    SELECT vh FROM VersionHistoryEntity vh
    JOIN vh.workloadInstance wi
    JOIN wi.workload w
    WHERE (w.team IS NULL OR w.team = '')
    AND vh.detectedAt >= :startDate
    AND vh.detectedAt <= :endDate
    ORDER BY vh.detectedAt DESC
  """)
  fun findByUnassignedTeamAndDateRange(
    @Param("startDate") startDate: Instant,
    @Param("endDate") endDate: Instant
  ): List<VersionHistoryEntity>

  /**
   * Find all version history entries within a date range.
   * Used for aggregate metrics calculations.
   */
  @Query("""
    SELECT vh FROM VersionHistoryEntity vh
    WHERE vh.detectedAt >= :startDate
    AND vh.detectedAt <= :endDate
    ORDER BY vh.detectedAt DESC
  """)
  fun findByDateRange(
    @Param("startDate") startDate: Instant,
    @Param("endDate") endDate: Instant
  ): List<VersionHistoryEntity>
}
