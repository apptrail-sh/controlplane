package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import sh.apptrail.controlplane.infrastructure.persistence.entity.ClusterEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadInstanceEntity

interface WorkloadInstanceRepository : JpaRepository<WorkloadInstanceEntity, Long> {
  fun findByWorkloadAndClusterAndNamespace(
    workload: WorkloadEntity,
    cluster: ClusterEntity,
    namespace: String,
  ): WorkloadInstanceEntity?

  fun findByWorkloadIn(workloads: List<WorkloadEntity>): List<WorkloadInstanceEntity>
}
