package sh.apptrail.controlplane.infrastructure.ingress.http

import org.springframework.stereotype.Component
import sh.apptrail.controlplane.application.service.agent.AgentEventProcessorService
import sh.apptrail.controlplane.application.model.agent.AgentEventPayload

@Component
class HttpAgentEventIngester(
  private val processor: AgentEventProcessorService,
  private val mapper: HttpAgentEventMapper,
) {
  fun ingest(payload: AgentEventPayload) {
    val event = mapper.toAgentEvent(payload)
    processor.processEvent(event)
  }
}
