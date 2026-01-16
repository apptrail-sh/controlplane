package sh.apptrail.controlplane.application.service

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.persistence.entity.WorkloadEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadRepository
import sh.apptrail.controlplane.web.dto.UpdateWorkloadRequest

@Service
class WorkloadService(
  private val workloadRepository: WorkloadRepository,
) {

  @Transactional
  fun updateWorkload(id: Long, request: UpdateWorkloadRequest): WorkloadEntity? {
    val workload = workloadRepository.findById(id).orElse(null)
      ?: return null

    // Update fields if provided (PATCH semantics)
    // Empty strings clear the field, null values are ignored
    request.repositoryUrl?.let { workload.repositoryUrl = it.ifBlank { null } }
    request.description?.let { workload.description = it.ifBlank { null } }

    return workloadRepository.save(workload)
  }
}
