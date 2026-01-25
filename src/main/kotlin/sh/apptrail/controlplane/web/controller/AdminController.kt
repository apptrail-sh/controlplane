package sh.apptrail.controlplane.web.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.application.service.CellBackfillService

@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
  private val cellBackfillService: CellBackfillService,
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
}

data class BackfillResponse(
  val totalProcessed: Int,
  val updatedCount: Int,
)
