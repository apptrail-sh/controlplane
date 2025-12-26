package sh.apptrail.controlplane.infrastructure.ingress.pubsub

import org.springframework.stereotype.Component
import sh.apptrail.controlplane.application.model.agent.AgentEventPayload
import sh.apptrail.controlplane.application.service.agent.AgentEventProcessorService

@Component
class GCPPubSubAgentEventIngester(
  private val processor: AgentEventProcessorService,
  private val mapper: GCPPubSubAgentEventMapper,
) {
  fun ingest(payload: AgentEventPayload) {
    val event = mapper.toAgentEvent(payload)
    processor.processEvent(event)
  }
}
