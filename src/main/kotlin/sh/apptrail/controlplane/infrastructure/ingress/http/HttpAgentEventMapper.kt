package sh.apptrail.controlplane.infrastructure.ingress.http

import org.springframework.stereotype.Component
import sh.apptrail.controlplane.application.model.agent.AgentEvent
import sh.apptrail.controlplane.application.model.agent.AgentEventPayload

@Component
class HttpAgentEventMapper {
  fun toAgentEvent(payload: AgentEventPayload): AgentEvent {
    return AgentEvent(
      eventId = payload.eventId,
      occurredAt = payload.occurredAt,
      source = payload.source,
      workload = payload.workload,
      labels = payload.labels,
      kind = payload.kind,
      outcome = payload.outcome,
      revision = payload.revision,
      phase = payload.phase,
      error = payload.error,
    )
  }
}
