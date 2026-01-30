package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import sh.apptrail.controlplane.infrastructure.persistence.entity.PodEntity

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
}
