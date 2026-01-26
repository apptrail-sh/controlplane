package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity
import java.time.Instant

interface VersionHistoryRepository : JpaRepository<VersionHistoryEntity, Long> {
  fun findByWorkloadInstance_IdOrderByDetectedAtDesc(workloadInstanceId: Long): List<VersionHistoryEntity>
  fun findTopByWorkloadInstance_IdOrderByDetectedAtDesc(workloadInstanceId: Long): VersionHistoryEntity?

  /**
   * Find version history entries without a release that have a workload with a repository URL.
   */
  @Query("""
    SELECT vh FROM VersionHistoryEntity vh
    JOIN vh.workloadInstance wi
    JOIN wi.workload w
    WHERE vh.release IS NULL
    AND w.repositoryUrl IS NOT NULL
    AND w.repositoryUrl <> ''
  """)
  fun findByReleaseIsNullAndHasRepositoryUrl(pageable: Pageable): List<VersionHistoryEntity>

  /**
   * Find version history entries without a release for a specific repository URL.
   */
  @Query("""
    SELECT vh FROM VersionHistoryEntity vh
    JOIN vh.workloadInstance wi
    JOIN wi.workload w
    WHERE vh.release IS NULL
    AND LOWER(w.repositoryUrl) = LOWER(:repositoryUrl)
  """)
  fun findByReleaseIsNullAndWorkloadInstanceWorkloadRepositoryUrl(
    @Param("repositoryUrl") repositoryUrl: String
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
