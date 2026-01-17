package sh.apptrail.controlplane.web.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.infrastructure.config.ClusterEnvironmentProperties

@RestController
@RequestMapping("/api/v1/environments")
class EnvironmentController(
  private val properties: ClusterEnvironmentProperties,
) {
  @GetMapping
  fun getEnvironments(): EnvironmentsResponse {
    return EnvironmentsResponse(
      environments = properties.environmentOrder.mapIndexed { index, name ->
        EnvironmentInfo(name = name, order = index)
      }
    )
  }
}

data class EnvironmentsResponse(val environments: List<EnvironmentInfo>)
data class EnvironmentInfo(val name: String, val order: Int)
