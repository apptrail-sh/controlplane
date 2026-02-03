package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import sh.apptrail.controlplane.infrastructure.persistence.entity.ClusterEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.ClusterStatus
import java.time.Instant

interface ClusterRepository : JpaRepository<ClusterEntity, Long> {
  fun findByName(clusterId: String): ClusterEntity?

  fun findByStatus(status: ClusterStatus): List<ClusterEntity>

  @Query("""
    SELECT c FROM ClusterEntity c
    WHERE c.status = :status
    AND c.lastHeartbeatAt < :threshold
  """)
  fun findByStatusAndLastHeartbeatAtBefore(status: ClusterStatus, threshold: Instant): List<ClusterEntity>

  @Query("""
    SELECT c FROM ClusterEntity c
    WHERE c.status = 'ONLINE'
    AND (c.lastHeartbeatAt IS NULL OR c.lastHeartbeatAt < :threshold)
  """)
  fun findOnlineClustersWithNoRecentHeartbeat(threshold: Instant): List<ClusterEntity>

  @Modifying
  @Query("""
    UPDATE ClusterEntity c
    SET c.status = :status, c.updatedAt = CURRENT_TIMESTAMP
    WHERE c.id IN :clusterIds
  """)
  fun updateStatusByIds(clusterIds: List<Long>, status: ClusterStatus): Int
}
