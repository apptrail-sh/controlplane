package sh.apptrail.controlplane.application.model.agent

import java.time.Instant

enum class AgentEventKind {
  DEPLOYMENT,
}

enum class AgentEventOutcome {
  SUCCEEDED,
  FAILED,
}

enum class WorkloadKind {
  DEPLOYMENT,
  STATEFULSET,
  DAEMONSET,
  JOB,
  CRONJOB,
}

enum class DeploymentPhase {
  PENDING,
  PROGRESSING,
  COMPLETED,
  FAILED,
}

data class SourceMetadata(
  val clusterId: String,
  val agentVersion: String,
)

data class WorkloadRef(
  val kind: WorkloadKind,
  val name: String,
  val namespace: String,
)

data class Revision(
  val current: String,
  val previous: String?,
)

data class ErrorDetail(
  val code: String?,
  val message: String,
  val detail: String?,
)

data class AgentEvent(
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
