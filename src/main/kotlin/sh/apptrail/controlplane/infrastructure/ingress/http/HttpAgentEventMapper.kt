package sh.apptrail.controlplane.infrastructure.ingress.http

import org.springframework.stereotype.Component
import sh.apptrail.controlplane.application.model.agent.AgentEvent
import sh.apptrail.controlplane.application.model.agent.AgentEventPayload
import sh.apptrail.controlplane.application.service.ClusterTopologyResolver

@Component
class HttpAgentEventMapper(
  private val clusterTopologyResolver: ClusterTopologyResolver,
) {
  fun toAgentEvent(payload: AgentEventPayload): AgentEvent {
    val environment = clusterTopologyResolver.resolveEnvironment(payload.source.clusterId)
    return AgentEvent(
      eventId = payload.eventId,
      occurredAt = payload.occurredAt,
      environment = environment,
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
