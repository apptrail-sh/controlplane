package sh.apptrail.controlplane.infrastructure.ingress.http

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import sh.apptrail.controlplane.application.model.agent.AgentEventPayload
import sh.apptrail.controlplane.application.service.agent.AgentEventProcessorService

@Component
@ConditionalOnProperty(prefix = "apptrail.ingest.http", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class HttpAgentEventIngester(
  private val processor: AgentEventProcessorService,
  private val mapper: HttpAgentEventMapper,
) {
  fun ingest(payload: AgentEventPayload) {
    val event = mapper.toAgentEvent(payload)
    processor.processEvent(event)
  }
}
