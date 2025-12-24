package sh.apptrail.controlplane.repository

import org.springframework.data.jpa.repository.JpaRepository
import sh.apptrail.controlplane.entity.WorkloadEntity

interface WorkloadRepository : JpaRepository<WorkloadEntity, Long>
