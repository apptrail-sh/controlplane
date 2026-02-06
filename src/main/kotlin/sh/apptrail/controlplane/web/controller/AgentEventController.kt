package sh.apptrail.controlplane.web.controller

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.application.model.agent.AgentEventPayload
import sh.apptrail.controlplane.application.model.infrastructure.ResourceEventPayload
import sh.apptrail.controlplane.application.service.infrastructure.ResourceEventProcessorService
import sh.apptrail.controlplane.infrastructure.ingress.http.HttpAgentEventIngester

@RestController
@RequestMapping("/ingest/v1/agent/events")
@ConditionalOnProperty(prefix = "apptrail.ingest.http", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class AgentEventController(
  private val ingester: HttpAgentEventIngester,
  private val resourceEventProcessor: ResourceEventProcessorService
) {
  @PostMapping
  fun ingest(@RequestBody payload: AgentEventPayload): ResponseEntity<Void> {
    ingester.ingest(payload)
    return ResponseEntity.accepted().build()
  }

  @PostMapping("/batch")
  fun ingestBatch(@RequestBody events: List<ResourceEventPayload>): ResponseEntity<Void> {
    resourceEventProcessor.processBatch(events)
    return ResponseEntity.accepted().build()
  }
}
