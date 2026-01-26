package sh.apptrail.controlplane.web.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.application.service.CellBackfillService
import sh.apptrail.controlplane.application.service.ReleaseService

@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
  private val cellBackfillService: CellBackfillService,
  private val releaseService: ReleaseService,
) {

  @PostMapping("/backfill/cells")
  fun triggerCellBackfill(): ResponseEntity<BackfillResponse> {
    val result = cellBackfillService.runBackfill()
    return ResponseEntity.ok(
      BackfillResponse(
        totalProcessed = result.totalProcessed,
        updatedCount = result.updatedCount,
      )
    )
  }

  @PostMapping("/backfill-releases")
  fun triggerReleaseBackfill(
    @RequestParam(required = false) workloadId: Long?
  ): ResponseEntity<ReleaseBackfillResponse> {
    val linkedCount = if (workloadId != null) {
      releaseService.backfillReleasesForWorkload(workloadId)
    } else {
      releaseService.backfillAllReleases()
    }
    return ResponseEntity.ok(ReleaseBackfillResponse(linkedCount = linkedCount))
  }
}

data class BackfillResponse(
  val totalProcessed: Int,
  val updatedCount: Int,
)

data class ReleaseBackfillResponse(
  val linkedCount: Int,
)
