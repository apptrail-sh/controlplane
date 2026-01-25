package sh.apptrail.controlplane.web.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.application.service.InstanceMetricsRequest
import sh.apptrail.controlplane.application.service.InstanceMetricsResponse
import sh.apptrail.controlplane.application.service.InstanceMetricsService
import sh.apptrail.controlplane.application.service.MetricsQueryService
import sh.apptrail.controlplane.application.service.MetricQueryTemplate
import sh.apptrail.controlplane.application.service.MetricsStatusResponse

@RestController
@RequestMapping("/api/v1/metrics-queries")
class MetricsQueryController(
  private val metricsQueryService: MetricsQueryService,
  private val instanceMetricsService: InstanceMetricsService,
) {

  @GetMapping("/status")
  fun getStatus(): MetricsStatusResponse {
    return instanceMetricsService.getMetricsStatus()
  }

  @GetMapping
  fun getTemplates(): MetricsQueryTemplatesResponse {
    return MetricsQueryTemplatesResponse(templates = metricsQueryService.getQueryTemplates())
  }

  @PostMapping("/instance")
  fun getInstanceMetrics(@RequestBody request: InstanceMetricsApiRequest): InstanceMetricsResponse {
    val serviceRequest = InstanceMetricsRequest(
      clusterName = request.clusterName,
      clusterId = request.clusterId,
      namespace = request.namespace,
      environment = request.environment,
      cell = request.cell,
      workloadName = request.workloadName,
      workloadKind = request.workloadKind,
      team = request.team,
      version = request.version,
      includeSparklines = request.includeSparklines,
    )
    return instanceMetricsService.getInstanceMetrics(serviceRequest)
  }
}

data class MetricsQueryTemplatesResponse(
  val templates: List<MetricQueryTemplate>,
)

data class InstanceMetricsApiRequest(
  val clusterName: String,
  val clusterId: Long? = null,
  val namespace: String,
  val environment: String,
  val cell: String? = null,
  val workloadName: String,
  val workloadKind: String,
  val team: String? = null,
  val version: String? = null,
  val includeSparklines: Boolean = false,
)
