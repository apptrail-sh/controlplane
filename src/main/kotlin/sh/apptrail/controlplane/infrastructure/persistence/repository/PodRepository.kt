package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import sh.apptrail.controlplane.infrastructure.persistence.entity.PodEntity
import java.time.Instant

interface PodRepository : JpaRepository<PodEntity, Long> {

  fun findByClusterIdAndNamespaceAndName(clusterId: Long, namespace: String, name: String): PodEntity?

  fun findByClusterIdAndNamespaceAndDeletedAtIsNull(clusterId: Long, namespace: String): List<PodEntity>

  fun findByWorkloadInstanceIdAndDeletedAtIsNull(workloadInstanceId: Long): List<PodEntity>

  fun findByNodeIdAndDeletedAtIsNull(nodeId: Long): List<PodEntity>

  @Query("""
    SELECT p FROM PodEntity p
    WHERE p.cluster.id = :clusterId
    AND p.namespace = :namespace
    AND p.deletedAt IS NULL
    ORDER BY p.name
  """)
  fun findActivePodsInNamespace(clusterId: Long, namespace: String): List<PodEntity>

  @Query("""
    SELECT p FROM PodEntity p
    WHERE p.workloadInstance.id = :workloadInstanceId
    AND p.deletedAt IS NULL
    ORDER BY p.name
  """)
  fun findActivePodsByWorkloadInstance(workloadInstanceId: Long): List<PodEntity>

  @Query("""
    SELECT p FROM PodEntity p
    WHERE p.node.id = :nodeId
    AND p.deletedAt IS NULL
    ORDER BY p.namespace, p.name
  """)
  fun findActivePodsByNode(nodeId: Long): List<PodEntity>

  fun findByClusterIdAndUid(clusterId: Long, uid: String): PodEntity?

  @Query("SELECT p.uid FROM PodEntity p WHERE p.cluster.id = :clusterId AND p.deletedAt IS NULL")
  fun findActiveUidsByClusterId(clusterId: Long): List<String>

  @Modifying
  @Query("""
    UPDATE PodEntity p
    SET p.deletedAt = :deletedAt, p.updatedAt = :deletedAt
    WHERE p.cluster.id = :clusterId
    AND p.deletedAt IS NULL
    AND p.uid NOT IN :activeUids
    AND p.lastUpdatedAt < :heartbeatOccurredAt
  """)
  fun softDeleteNotInUidSet(clusterId: Long, activeUids: Set<String>, deletedAt: Instant, heartbeatOccurredAt: Instant): Int

  @Modifying
  @Query("""
    UPDATE PodEntity p
    SET p.deletedAt = :deletedAt, p.updatedAt = :deletedAt
    WHERE p.cluster.id = :clusterId
    AND p.deletedAt IS NULL
  """)
  fun markAllAsDeletedForCluster(clusterId: Long, deletedAt: Instant): Int

  @Modifying
  @Query("DELETE FROM PodEntity p WHERE p.deletedAt IS NOT NULL AND p.deletedAt < :threshold")
  fun hardDeleteOlderThan(threshold: Instant): Int
}
