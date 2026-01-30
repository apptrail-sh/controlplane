package sh.apptrail.controlplane.application.model.infrastructure

import java.time.Instant

enum class ResourceType {
  WORKLOAD,
  NODE,
  POD,
  SERVICE
}

enum class ResourceEventKind {
  CREATED,
  UPDATED,
  DELETED,
  STATUS_CHANGE
}

data class SourceMetadata(
  val clusterId: String,
  val agentVersion: String
)

data class ResourceRef(
  val kind: String,
  val name: String,
  val namespace: String? = null,
  val uid: String
)

data class ResourceState(
  val phase: String? = null,
  val conditions: List<Condition>? = null,
  val metrics: Map<String, String>? = null
)

data class Condition(
  val type: String,
  val status: String,
  val reason: String? = null,
  val message: String? = null
)

data class NodeMetadata(
  val kubeletVersion: String? = null,
  val containerRuntimeVersion: String? = null,
  val osImage: String? = null,
  val architecture: String? = null,
  val capacity: Map<String, String>? = null,
  val allocatable: Map<String, String>? = null,
  val taints: List<NodeTaint>? = null
)

data class NodeTaint(
  val key: String,
  val value: String? = null,
  val effect: String
)

data class PodMetadata(
  val ownerKind: String? = null,
  val ownerName: String? = null,
  val ownerUID: String? = null,
  val nodeName: String? = null,
  val podIP: String? = null,
  val startTime: Instant? = null,
  val restartCount: Int = 0,
  val containers: List<ContainerStatusInfo>? = null,
  val initContainers: List<ContainerStatusInfo>? = null
)

data class ContainerStatusInfo(
  val name: String,
  val image: String? = null,
  val ready: Boolean = false,
  val restartCount: Int = 0,
  val state: String? = null,
  val reason: String? = null,
  val message: String? = null
)

data class ResourceEventPayload(
  val eventId: String,
  val occurredAt: Instant,
  val source: SourceMetadata,
  val resourceType: ResourceType,
  val resource: ResourceRef,
  val labels: Map<String, String>? = null,
  val eventKind: ResourceEventKind,
  val state: ResourceState? = null,
  val metadata: Map<String, Any?>? = null
)
