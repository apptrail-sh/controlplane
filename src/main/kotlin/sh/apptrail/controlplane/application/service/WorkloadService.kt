package sh.apptrail.controlplane.application.service

import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadRepository
import sh.apptrail.controlplane.web.dto.UpdateWorkloadRequest

@Service
class WorkloadService(
  private val workloadRepository: WorkloadRepository,
  private val releaseService: ReleaseService,
  private val repositoryService: RepositoryService,
) {
  private val log = LoggerFactory.getLogger(WorkloadService::class.java)

  @Transactional
  fun updateWorkload(id: Long, request: UpdateWorkloadRequest): WorkloadEntity? {
    val workload = workloadRepository.findById(id).orElse(null)
      ?: return null

    val originalRepository = workload.repository

    // Update fields if provided (PATCH semantics)
    // Empty strings clear the field, null values are ignored
    request.repositoryUrl?.let { url ->
      workload.repository = if (url.isBlank()) null else repositoryService.findOrCreate(url)
    }
    request.description?.let { workload.description = it.ifBlank { null } }

    val savedWorkload = workloadRepository.save(workload)

    // If repository was set/changed, backfill version history releases
    if (savedWorkload.repository != null && savedWorkload.repository != originalRepository) {
      val linkedCount = releaseService.backfillReleasesForWorkload(savedWorkload.id!!)
      if (linkedCount > 0) {
        log.info("Backfilled {} version history entries after repository update for workload {}",
          linkedCount, savedWorkload.name)
      }
    }

    return savedWorkload
  }
}
