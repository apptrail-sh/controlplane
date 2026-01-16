package sh.apptrail.controlplane.application.model.agent

import java.time.Instant

data class AgentEventPayload(
  val eventId: String,
  val occurredAt: Instant,
  val source: SourceMetadata,
  val workload: WorkloadRef,
  val labels: Map<String, String>,
  val kind: AgentEventKind,
  val outcome: AgentEventOutcome?,
  val revision: Revision?,
  val phase: DeploymentPhase?,
  val error: ErrorDetail?,
)
