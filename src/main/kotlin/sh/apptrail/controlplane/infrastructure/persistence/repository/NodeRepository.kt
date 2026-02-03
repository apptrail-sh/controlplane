package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import sh.apptrail.controlplane.infrastructure.persistence.entity.NodeEntity
import java.time.Instant

interface NodeRepository : JpaRepository<NodeEntity, Long> {

  fun findByClusterIdAndName(clusterId: Long, name: String): NodeEntity?

  fun findByClusterIdAndDeletedAtIsNull(clusterId: Long): List<NodeEntity>

  @Query("SELECT n FROM NodeEntity n WHERE n.cluster.id = :clusterId AND n.deletedAt IS NULL ORDER BY n.name")
  fun findActiveNodesByClusterId(clusterId: Long): List<NodeEntity>

  fun findByClusterIdAndUid(clusterId: Long, uid: String): NodeEntity?

  @Query("SELECT n.uid FROM NodeEntity n WHERE n.cluster.id = :clusterId AND n.deletedAt IS NULL")
  fun findActiveUidsByClusterId(clusterId: Long): List<String>

  @Modifying
  @Query("""
    UPDATE NodeEntity n
    SET n.deletedAt = :deletedAt, n.updatedAt = :deletedAt
    WHERE n.cluster.id = :clusterId
    AND n.deletedAt IS NULL
    AND n.uid NOT IN :activeUids
  """)
  fun softDeleteNotInUidSet(clusterId: Long, activeUids: Set<String>, deletedAt: Instant): Int

  @Modifying
  @Query("""
    UPDATE NodeEntity n
    SET n.deletedAt = :deletedAt, n.updatedAt = :deletedAt
    WHERE n.cluster.id = :clusterId
    AND n.deletedAt IS NULL
  """)
  fun markAllAsDeletedForCluster(clusterId: Long, deletedAt: Instant): Int

  @Modifying
  @Query("DELETE FROM NodeEntity n WHERE n.deletedAt IS NOT NULL AND n.deletedAt < :threshold")
  fun hardDeleteOlderThan(threshold: Instant): Int
}
