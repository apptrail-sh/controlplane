package sh.apptrail.controlplane.web.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sh.apptrail.controlplane.application.service.infrastructure.PodService
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadInstanceRepository
import sh.apptrail.controlplane.web.dto.PodResponse
import sh.apptrail.controlplane.web.dto.toResponse

@RestController
@RequestMapping("/api/v1/workload-instances")
class WorkloadInstanceController(
  private val workloadInstanceRepository: WorkloadInstanceRepository,
  private val podService: PodService,
) {

  @GetMapping("/{id}/pods")
  fun getPods(@PathVariable id: Long): ResponseEntity<List<PodResponse>> {
    workloadInstanceRepository.findById(id).orElse(null)
      ?: return ResponseEntity.notFound().build()
    val pods = podService.findActivePodsByWorkloadInstance(id)
    return ResponseEntity.ok(pods.map { it.toResponse() })
  }
}
