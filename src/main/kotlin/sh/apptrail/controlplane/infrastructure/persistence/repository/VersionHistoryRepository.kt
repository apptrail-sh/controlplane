package sh.apptrail.controlplane.infrastructure.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import sh.apptrail.controlplane.infrastructure.persistence.entity.VersionHistoryEntity

interface VersionHistoryRepository : JpaRepository<VersionHistoryEntity, Long>
