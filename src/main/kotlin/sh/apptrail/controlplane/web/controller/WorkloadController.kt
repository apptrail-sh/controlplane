package sh.apptrail.controlplane.web.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.service.WorkloadService

@RestController
@RequestMapping("/api/workloads")
class WorkloadController(
  private val workloadService: WorkloadService,
) {
  @GetMapping
  fun listWorkloads() = emptyList<String>()
}
