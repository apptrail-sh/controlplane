package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import sh.apptrail.controlplane.infrastructure.persistence.entity.ImpactAnalysisStatus
import sh.apptrail.controlplane.infrastructure.persistence.entity.VersionImpactAnalysisEntity
import java.time.Instant

interface VersionImpactAnalysisRepository : JpaRepository<VersionImpactAnalysisEntity, Long> {

  fun findByVersionHistoryId(versionHistoryId: Long): VersionImpactAnalysisEntity?

  fun findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
    status: ImpactAnalysisStatus,
    scheduledAt: Instant
  ): List<VersionImpactAnalysisEntity>

  @Query("""
    SELECT via FROM VersionImpactAnalysisEntity via
    JOIN via.versionHistory vh
    JOIN vh.workloadInstance wi
    WHERE wi.id = :workloadInstanceId
    ORDER BY via.createdAt DESC
  """)
  fun findByWorkloadInstanceIdOrderByCreatedAtDesc(workloadInstanceId: Long): List<VersionImpactAnalysisEntity>

  @Query("""
    SELECT via FROM VersionImpactAnalysisEntity via
    JOIN via.versionHistory vh
    JOIN vh.workloadInstance wi
    JOIN wi.workload w
    WHERE w.id = :workloadId
    ORDER BY via.createdAt DESC
  """)
  fun findByWorkloadIdOrderByCreatedAtDesc(workloadId: Long): List<VersionImpactAnalysisEntity>
}
