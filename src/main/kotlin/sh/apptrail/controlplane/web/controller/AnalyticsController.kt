package sh.apptrail.controlplane.web.controller

import org.springframework.web.bind.annotation.*
import sh.apptrail.controlplane.application.service.AnalyticsFilters
import sh.apptrail.controlplane.application.service.AnalyticsFiltersResponse
import sh.apptrail.controlplane.application.service.AnalyticsService
import sh.apptrail.controlplane.application.service.DashboardOverviewResponse

@RestController
@RequestMapping("/api/v1/analytics")
class AnalyticsController(
  private val analyticsService: AnalyticsService
) {

  @GetMapping("/filters")
  fun getFilters(): AnalyticsFiltersResponse {
    return analyticsService.getAvailableFilters()
  }

  @GetMapping("/overview")
  fun getOverview(
    @RequestParam(required = false) startDate: String?,
    @RequestParam(required = false) endDate: String?,
    @RequestParam(required = false) environment: String?,
    @RequestParam(required = false) team: String?,
    @RequestParam(required = false) workloadId: Long?,
    @RequestParam(required = false) clusterId: Long?,
    @RequestParam(required = false, defaultValue = "day") granularity: String?
  ): DashboardOverviewResponse {
    val filters = AnalyticsFilters(
      startDate = startDate,
      endDate = endDate,
      environment = environment,
      team = team,
      workloadId = workloadId,
      clusterId = clusterId,
      granularity = granularity
    )
    return analyticsService.getOverview(filters)
  }
}
