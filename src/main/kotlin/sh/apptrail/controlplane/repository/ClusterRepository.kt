package sh.apptrail.controlplane.repository

import org.springframework.data.jpa.repository.JpaRepository
import sh.apptrail.controlplane.entity.ClusterEntity

interface ClusterRepository : JpaRepository<ClusterEntity, Long>
