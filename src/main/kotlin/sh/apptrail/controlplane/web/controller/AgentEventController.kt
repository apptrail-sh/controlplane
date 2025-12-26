package sh.apptrail.controlplane.web.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.application.model.agent.AgentEventPayload
import sh.apptrail.controlplane.infrastructure.ingress.http.HttpAgentEventIngester

@RestController
@RequestMapping("/api/agent/events")
class AgentEventController(
  private val ingester: HttpAgentEventIngester,
) {
  @PostMapping
  fun ingest(@RequestBody payload: AgentEventPayload): ResponseEntity<Void> {
    ingester.ingest(payload)
    return ResponseEntity.accepted().build()
  }
}
