package sh.apptrail.controlplane.web.controller

import org.springframework.web.bind.annotation.*
import sh.apptrail.controlplane.application.service.AnalyticsFilters
import sh.apptrail.controlplane.application.service.AnalyticsFiltersResponse
import sh.apptrail.controlplane.application.service.AnalyticsService
import sh.apptrail.controlplane.application.service.DashboardOverviewResponse
import sh.apptrail.controlplane.application.service.MetricsFilters
import sh.apptrail.controlplane.application.service.TeamScorecardsResponse
import sh.apptrail.controlplane.application.service.TeamScorecardService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RestController
@RequestMapping("/api/v1/analytics")
class AnalyticsController(
  private val analyticsService: AnalyticsService,
  private val teamScorecardService: TeamScorecardService
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

  @GetMapping("/scorecards")
  fun getAllTeamScorecards(
    @RequestParam(required = false) startDate: String?,
    @RequestParam(required = false) endDate: String?,
    @RequestParam(required = false) environment: String?,
    @RequestParam(required = false) clusterId: Long?,
    @RequestParam(required = false, defaultValue = "day") granularity: String
  ): TeamScorecardsResponse {
    val parsedStartDate = startDate?.let { parseDate(it) }
      ?: LocalDate.now().minusDays(30).atStartOfDay().toInstant(ZoneOffset.UTC)
    val parsedEndDate = endDate?.let { parseDate(it) }
      ?: Instant.now()

    val filters = MetricsFilters(
      startDate = parsedStartDate,
      endDate = parsedEndDate,
      environment = environment,
      clusterId = clusterId,
      granularity = granularity
    )

    return teamScorecardService.getAllTeamScorecards(filters)
  }

  private fun parseDate(dateStr: String): Instant {
    return if (dateStr.contains("T")) {
      Instant.parse(dateStr)
    } else {
      LocalDate.parse(dateStr).atStartOfDay().toInstant(ZoneOffset.UTC)
    }
  }
}
