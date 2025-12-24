package sh.apptrail.controlplane.controllers

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.entity.Workload

@RestController
@RequestMapping("/v1/workload")
class WorkloadController {

  @GetMapping
  fun getWorkloads(): List<Workload> {
    return emptyList()
  }
}
