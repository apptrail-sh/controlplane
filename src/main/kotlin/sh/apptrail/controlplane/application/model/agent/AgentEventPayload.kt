package sh.apptrail.controlplane.application.model.agent

data class AgentEventPayload(
  val id: String,
  val metadata: AgentEventMetadata,
  val labels: Map<String, String>,
  val type: AgentEventType,
  val workloadType: WorkloadMetadata,
  val currentVersion: String,
  val previousVersion: String?,
  val deploymentPhase: String?,
  val statusMessage: String?,
  val statusReason: String?,
)
