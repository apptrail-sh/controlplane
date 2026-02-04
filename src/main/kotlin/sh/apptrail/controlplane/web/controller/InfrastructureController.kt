package sh.apptrail.controlplane.web.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sh.apptrail.controlplane.application.service.infrastructure.NodeMetricsResponse
import sh.apptrail.controlplane.application.service.infrastructure.NodeMetricsService
import sh.apptrail.controlplane.application.service.infrastructure.NodeService
import sh.apptrail.controlplane.application.service.infrastructure.PodMetricsResponse
import sh.apptrail.controlplane.application.service.infrastructure.PodMetricsService
import sh.apptrail.controlplane.application.service.infrastructure.PodService
import sh.apptrail.controlplane.infrastructure.persistence.entity.*
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterRepository
import java.time.Instant

@RestController
@RequestMapping("/api/v1/clusters/{clusterId}")
class InfrastructureController(
  private val clusterRepository: ClusterRepository,
  private val nodeService: NodeService,
  private val podService: PodService,
  private val nodeMetricsService: NodeMetricsService,
  private val podMetricsService: PodMetricsService,
) {

  @GetMapping("/nodes")
  fun getNodes(@PathVariable clusterId: Long): ResponseEntity<List<NodeResponse>> {
    val nodes = nodeService.findActiveNodesByCluster(clusterId)
    return ResponseEntity.ok(nodes.map { it.toResponse() })
  }

  @GetMapping("/nodes/{nodeName}")
  fun getNode(
    @PathVariable clusterId: Long,
    @PathVariable nodeName: String
  ): ResponseEntity<NodeResponse> {
    val node = nodeService.findNodeByClusterAndName(clusterId, nodeName)
      ?: return ResponseEntity.notFound().build()
    return ResponseEntity.ok(node.toResponse())
  }

  @GetMapping("/nodes/{nodeName}/metrics")
  fun getNodeMetrics(
    @PathVariable clusterId: Long,
    @PathVariable nodeName: String,
  ): ResponseEntity<NodeMetricsResponse> {
    val metrics = nodeMetricsService.getNodeMetrics(clusterId, nodeName)
    return ResponseEntity.ok(metrics)
  }

  @GetMapping("/nodes/{nodeName}/pods")
  fun getPodsOnNode(
    @PathVariable clusterId: Long,
    @PathVariable nodeName: String
  ): ResponseEntity<List<PodResponse>> {
    val node = nodeService.findNodeByClusterAndName(clusterId, nodeName)
      ?: return ResponseEntity.notFound().build()
    val pods = podService.findActivePodsByNode(node.id!!)
    return ResponseEntity.ok(pods.map { it.toResponse() })
  }

  @GetMapping("/namespaces/{namespace}/pods")
  fun getPodsInNamespace(
    @PathVariable clusterId: Long,
    @PathVariable namespace: String
  ): ResponseEntity<List<PodResponse>> {
    val pods = podService.findActivePodsInNamespace(clusterId, namespace)
    return ResponseEntity.ok(pods.map { it.toResponse() })
  }

  @GetMapping("/namespaces/{namespace}/pods/{podName}")
  fun getPod(
    @PathVariable clusterId: Long,
    @PathVariable namespace: String,
    @PathVariable podName: String
  ): ResponseEntity<PodDetailResponse> {
    val cluster = clusterRepository.findById(clusterId).orElse(null)
      ?: return ResponseEntity.notFound().build()
    val pods = podService.findActivePodsInNamespace(clusterId, namespace)
    val pod = pods.find { it.name == podName }
      ?: return ResponseEntity.notFound().build()
    return ResponseEntity.ok(pod.toDetailResponse())
  }

  @GetMapping("/namespaces/{namespace}/pods/{podName}/metrics")
  fun getPodMetrics(
    @PathVariable clusterId: Long,
    @PathVariable namespace: String,
    @PathVariable podName: String,
  ): ResponseEntity<PodMetricsResponse> {
    val metrics = podMetricsService.getPodMetrics(clusterId, namespace, podName)
    return ResponseEntity.ok(metrics)
  }
}

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

private fun NodeEntity.toResponse() = NodeResponse(
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

private fun PodEntity.toResponse() = PodResponse(
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

private fun PodEntity.toDetailResponse() = PodDetailResponse(
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
