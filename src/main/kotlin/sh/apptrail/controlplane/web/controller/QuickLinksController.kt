package sh.apptrail.controlplane.web.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.application.service.InterpolatedQuickLink
import sh.apptrail.controlplane.application.service.QuickLinkContext
import sh.apptrail.controlplane.application.service.QuickLinkService
import sh.apptrail.controlplane.application.service.QuickLinkTemplate

@RestController
@RequestMapping("/api/v1/quick-links")
class QuickLinksController(
  private val quickLinkService: QuickLinkService,
) {

  @GetMapping
  fun getAllTemplates(): QuickLinksResponse {
    return QuickLinksResponse(links = quickLinkService.getAllTemplates())
  }

  @PostMapping("/interpolate")
  fun interpolateLinks(@RequestBody request: InterpolateQuickLinksRequest): InterpolatedQuickLinksResponse {
    val context = QuickLinkContext(
      clusterName = request.clusterName,
      clusterId = request.clusterId,
      namespace = request.namespace,
      environment = request.environment,
      shard = request.shard,
      workloadName = request.workloadName,
      workloadKind = request.workloadKind,
      team = request.team,
      version = request.version,
    )
    return InterpolatedQuickLinksResponse(links = quickLinkService.getInterpolatedLinks(context))
  }
}

data class QuickLinksResponse(
  val links: List<QuickLinkTemplate>,
)

data class InterpolateQuickLinksRequest(
  val clusterName: String,
  val clusterId: Long? = null,
  val namespace: String,
  val environment: String,
  val shard: String? = null,
  val workloadName: String,
  val workloadKind: String,
  val team: String? = null,
  val version: String? = null,
)

data class InterpolatedQuickLinksResponse(
  val links: List<InterpolatedQuickLink>,
)
