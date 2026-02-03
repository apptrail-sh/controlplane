package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import sh.apptrail.controlplane.infrastructure.persistence.entity.ClusterHeartbeatEntity
import java.time.Instant

interface ClusterHeartbeatRepository : JpaRepository<ClusterHeartbeatEntity, Long> {

  fun findByClusterIdOrderByReceivedAtDesc(clusterId: Long): List<ClusterHeartbeatEntity>

  fun findTopByClusterIdOrderByReceivedAtDesc(clusterId: Long): ClusterHeartbeatEntity?

  @Modifying
  @Query("DELETE FROM ClusterHeartbeatEntity h WHERE h.receivedAt < :threshold")
  fun deleteOlderThan(threshold: Instant): Int
}
