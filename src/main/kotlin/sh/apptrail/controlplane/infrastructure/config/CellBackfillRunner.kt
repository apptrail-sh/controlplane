package sh.apptrail.controlplane.infrastructure.config

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import sh.apptrail.controlplane.application.service.CellBackfillService

/**
 * Startup task that backfills the cell field for existing workload instances
 * based on the current cell configuration.
 *
 * Enabled via `app.backfill.cell.enabled-on-startup=true`
 */
@Component
@ConditionalOnProperty(
  prefix = "app.backfill.cell",
  name = ["enabled-on-startup"],
  havingValue = "true",
  matchIfMissing = false
)
@EnableConfigurationProperties(CellBackfillProperties::class)
class CellBackfillRunner(
  private val cellBackfillService: CellBackfillService,
) {

  private val log = LoggerFactory.getLogger(CellBackfillRunner::class.java)

  @EventListener(ApplicationReadyEvent::class)
  fun onApplicationReady() {
    log.info("Running cell backfill on startup...")
    val result = cellBackfillService.runBackfill()
    log.info("Startup cell backfill completed: {} processed, {} updated", result.totalProcessed, result.updatedCount)
  }
}
