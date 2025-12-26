package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import sh.apptrail.controlplane.infrastructure.persistence.entity.ClusterEntity

interface ClusterRepository : JpaRepository<ClusterEntity, Long>
