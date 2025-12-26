package sh.apptrail.controlplane.application.model.agent

enum class AgentEventType {
  DEPLOYMENT_SUCCEEDED,
  DEPLOYMENT_FAILED,
}

data class AgentEventMetadata(val clusterId: String, val agentVersion: String)
data class WorkloadMetadata(val kind: String)

data class AgentEvent(
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
