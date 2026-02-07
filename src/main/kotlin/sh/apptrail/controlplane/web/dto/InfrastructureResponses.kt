package sh.apptrail.controlplane.web.dto

import sh.apptrail.controlplane.infrastructure.persistence.entity.NodeEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.PodEntity
import java.time.Instant

data class NodeResponse(
  val id: Long,
  val name: String,
  val uid: String,
  val labels: Map<String, String>?,
  val status: NodeStatusResponse?,
  val firstSeenAt: Instant,
  val lastUpdatedAt: Instant
)

data class NodeStatusResponse(
  val phase: String?,
  val conditions: List<ConditionResponse>?,
  val capacity: Map<String, String>?,
  val allocatable: Map<String, String>?,
  val nodeInfo: NodeInfoResponse?
)

data class NodeInfoResponse(
  val kubeletVersion: String?,
  val containerRuntimeVersion: String?,
  val osImage: String?,
  val architecture: String?
)

data class ConditionResponse(
  val type: String,
  val status: String,
  val reason: String?,
  val message: String?
)

data class PodResponse(
  val id: Long,
  val namespace: String,
  val name: String,
  val uid: String,
  val phase: String?,
  val nodeName: String?,
  val workloadInstanceId: Long?,
  val restartCount: Int,
  val firstSeenAt: Instant,
  val lastUpdatedAt: Instant
)

data class PodDetailResponse(
  val id: Long,
  val namespace: String,
  val name: String,
  val uid: String,
  val labels: Map<String, String>?,
  val status: PodStatusResponse?,
  val nodeName: String?,
  val nodeId: Long?,
  val workloadInstanceId: Long?,
  val firstSeenAt: Instant,
  val lastUpdatedAt: Instant
)

data class PodStatusResponse(
  val phase: String?,
  val conditions: List<ConditionResponse>?,
  val podIP: String?,
  val startTime: Instant?,
  val containerStatuses: List<ContainerStatusResponse>?,
  val initContainerStatuses: List<ContainerStatusResponse>?
)

data class ContainerStatusResponse(
  val name: String,
  val image: String?,
  val ready: Boolean,
  val restartCount: Int,
  val state: String?,
  val reason: String?,
  val message: String?
)

fun NodeEntity.toResponse() = NodeResponse(
  id = id!!,
  name = name,
  uid = uid,
  labels = labels,
  status = status?.let {
    NodeStatusResponse(
      phase = it.phase,
      conditions = it.conditions?.map { c ->
        ConditionResponse(c.type, c.status, c.reason, c.message)
      },
      capacity = it.capacity,
      allocatable = it.allocatable,
      nodeInfo = it.nodeInfo?.let { ni ->
        NodeInfoResponse(
          kubeletVersion = ni.kubeletVersion,
          containerRuntimeVersion = ni.containerRuntimeVersion,
          osImage = ni.osImage,
          architecture = ni.architecture
        )
      }
    )
  },
  firstSeenAt = firstSeenAt,
  lastUpdatedAt = lastUpdatedAt
)

fun PodEntity.toResponse() = PodResponse(
  id = id!!,
  namespace = namespace,
  name = name,
  uid = uid,
  phase = status?.phase,
  nodeName = node?.name,
  workloadInstanceId = workloadInstance?.id,
  restartCount = status?.containerStatuses?.sumOf { it.restartCount } ?: 0,
  firstSeenAt = firstSeenAt,
  lastUpdatedAt = lastUpdatedAt
)

fun PodEntity.toDetailResponse() = PodDetailResponse(
  id = id!!,
  namespace = namespace,
  name = name,
  uid = uid,
  labels = labels,
  status = status?.let {
    PodStatusResponse(
      phase = it.phase,
      conditions = it.conditions?.map { c ->
        ConditionResponse(c.type, c.status, c.reason, c.message)
      },
      podIP = it.podIP,
      startTime = it.startTime,
      containerStatuses = it.containerStatuses?.map { cs ->
        ContainerStatusResponse(
          name = cs.name,
          image = cs.image,
          ready = cs.ready,
          restartCount = cs.restartCount,
          state = cs.state,
          reason = cs.reason,
          message = cs.message
        )
      },
      initContainerStatuses = it.initContainerStatuses?.map { cs ->
        ContainerStatusResponse(
          name = cs.name,
          image = cs.image,
          ready = cs.ready,
          restartCount = cs.restartCount,
          state = cs.state,
          reason = cs.reason,
          message = cs.message
        )
      }
    )
  },
  nodeName = node?.name,
  nodeId = node?.id,
  workloadInstanceId = workloadInstance?.id,
  firstSeenAt = firstSeenAt,
  lastUpdatedAt = lastUpdatedAt
)
