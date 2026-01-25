package sh.apptrail.controlplane.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.backfill.cell")
data class CellBackfillProperties(
  val enabledOnStartup: Boolean = false,
)
