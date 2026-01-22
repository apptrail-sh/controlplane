package sh.apptrail.controlplane.web.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.application.service.AlertService
import sh.apptrail.controlplane.infrastructure.alerting.prometheus.AlertAggregation
import sh.apptrail.controlplane.infrastructure.alerting.prometheus.AlertInfo

@RestController
@RequestMapping("/api/v1/alerts")
class AlertController(
  private val alertService: AlertService,
) {
  @GetMapping("/summary")
  fun getAlertSummary(): ResponseEntity<AlertAggregation> {
    val aggregation = alertService.getAlertAggregations()
      ?: return ResponseEntity.status(503).build()
    return ResponseEntity.ok(aggregation)
  }

  @GetMapping("/critical")
  fun getCriticalAlerts(): ResponseEntity<List<AlertInfo>> {
    val alerts = alertService.getCriticalAlerts()
      ?: return ResponseEntity.status(503).build()
    return ResponseEntity.ok(alerts)
  }

  @GetMapping
  fun getAllFiringAlerts(): ResponseEntity<List<AlertInfo>> {
    val alerts = alertService.getAllFiringAlerts()
      ?: return ResponseEntity.status(503).build()
    return ResponseEntity.ok(alerts)
  }
}
