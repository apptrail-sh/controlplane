package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import sh.apptrail.controlplane.infrastructure.persistence.entity.NodeEntity

interface NodeRepository : JpaRepository<NodeEntity, Long> {

  fun findByClusterIdAndName(clusterId: Long, name: String): NodeEntity?

  fun findByClusterIdAndDeletedAtIsNull(clusterId: Long): List<NodeEntity>

  @Query("SELECT n FROM NodeEntity n WHERE n.cluster.id = :clusterId AND n.deletedAt IS NULL ORDER BY n.name")
  fun findActiveNodesByClusterId(clusterId: Long): List<NodeEntity>

  fun findByClusterIdAndUid(clusterId: Long, uid: String): NodeEntity?
}
