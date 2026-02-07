package sh.apptrail.controlplane.web.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sh.apptrail.controlplane.application.service.infrastructure.NodeMetricsResponse
import sh.apptrail.controlplane.application.service.infrastructure.NodeMetricsService
import sh.apptrail.controlplane.application.service.infrastructure.NodeService
import sh.apptrail.controlplane.application.service.infrastructure.PodMetricsResponse
import sh.apptrail.controlplane.application.service.infrastructure.PodMetricsService
import sh.apptrail.controlplane.application.service.infrastructure.PodService
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterRepository
import sh.apptrail.controlplane.web.dto.*

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
