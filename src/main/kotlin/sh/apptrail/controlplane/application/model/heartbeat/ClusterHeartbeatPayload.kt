package sh.apptrail.controlplane.application.model.heartbeat

import java.time.Instant

data class ClusterHeartbeatPayload(
  val eventId: String,
  val occurredAt: Instant,
  val source: SourceMetadata,
  val messageType: String = "HEARTBEAT",
  val inventory: ResourceInventory
)

data class SourceMetadata(
  val clusterId: String,
  val agentVersion: String
)

data class ResourceInventory(
  val nodeUids: List<String> = emptyList(),
  val podUids: List<String> = emptyList()
)
