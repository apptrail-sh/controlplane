package sh.apptrail.controlplane.infrastructure.ingress.pubsub

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import sh.apptrail.controlplane.application.model.agent.AgentEvent
import sh.apptrail.controlplane.application.model.agent.AgentEventPayload
import sh.apptrail.controlplane.application.service.ClusterTopologyResolver

@Component
@ConditionalOnProperty(prefix = "app.ingest.pubsub", name = ["enabled"], havingValue = "true")
class GCPPubSubAgentEventMapper(
  private val clusterTopologyResolver: ClusterTopologyResolver,
) {
  fun toAgentEvent(payload: AgentEventPayload): AgentEvent {
    val environment = clusterTopologyResolver.resolveEnvironment(
      payload.source.clusterId,
      payload.workload.namespace
    )
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
