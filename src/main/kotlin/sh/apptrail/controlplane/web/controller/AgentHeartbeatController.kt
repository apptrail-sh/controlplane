package sh.apptrail.controlplane.web.controller

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.application.model.heartbeat.ClusterHeartbeatPayload
import sh.apptrail.controlplane.application.service.cleanup.ClusterHeartbeatService

@RestController
@RequestMapping("/ingest/v1/agent/heartbeat")
@ConditionalOnProperty(prefix = "apptrail.ingest.http", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class AgentHeartbeatController(
  private val heartbeatService: ClusterHeartbeatService
) {

  @PostMapping
  fun receiveHeartbeat(@RequestBody payload: ClusterHeartbeatPayload): ResponseEntity<Void> {
    heartbeatService.processHeartbeat(payload)
    return ResponseEntity.accepted().build()
  }
}
