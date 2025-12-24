package sh.apptrail.controlplane.repository

import org.springframework.data.jpa.repository.JpaRepository
import sh.apptrail.controlplane.database.VersionHistoryEntity

interface VersionHistoryRepository : JpaRepository<VersionHistoryEntity, Long>
