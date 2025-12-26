package sh.apptrail.controlplane.infrastructure.ingress.http

import org.springframework.stereotype.Component
import sh.apptrail.controlplane.application.model.agent.AgentEvent
import sh.apptrail.controlplane.application.model.agent.AgentEventPayload

@Component
class HttpAgentEventMapper {
  fun toAgentEvent(payload: AgentEventPayload): AgentEvent {
    return AgentEvent(
      id = payload.id,
      metadata = payload.metadata,
      labels = payload.labels,
      type = payload.type,
      workloadType = payload.workloadType,
      currentVersion = payload.currentVersion,
      previousVersion = payload.previousVersion,
      deploymentPhase = payload.deploymentPhase,
      statusMessage = payload.statusMessage,
      statusReason = payload.statusReason,
    )
  }
}
