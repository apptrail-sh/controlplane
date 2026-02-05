package sh.apptrail.controlplane.application.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sh.apptrail.controlplane.infrastructure.persistence.entity.ClusterEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterRepository

@Service
class ClusterService(
  private val clusterRepository: ClusterRepository,
) {

  @Transactional
  fun findOrCreateCluster(clusterId: String): ClusterEntity {
    return clusterRepository.findByName(clusterId)
      ?: clusterRepository.save(ClusterEntity().apply { name = clusterId })
  }

  fun findCluster(clusterId: String): ClusterEntity? {
    return clusterRepository.findByName(clusterId)
  }
}
