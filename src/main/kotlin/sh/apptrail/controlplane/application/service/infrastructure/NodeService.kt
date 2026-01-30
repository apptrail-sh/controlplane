package sh.apptrail.controlplane.application.service.infrastructure

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sh.apptrail.controlplane.application.model.infrastructure.*
import sh.apptrail.controlplane.infrastructure.persistence.entity.*
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.NodeRepository
import java.time.Instant

@Service
class NodeService(
  private val nodeRepository: NodeRepository,
  private val clusterRepository: ClusterRepository,
  private val broadcaster: InfrastructureEventBroadcaster
) {

  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  fun processNodeEvent(event: ResourceEventPayload): NodeEntity? {
    val clusterId = event.source.clusterId
    val cluster = clusterRepository.findByName(clusterId)
      ?: run {
        log.warn("Cluster not found: $clusterId, skipping node event")
        return null
      }

    return when (event.eventKind) {
      ResourceEventKind.CREATED, ResourceEventKind.UPDATED, ResourceEventKind.STATUS_CHANGE ->
        upsertNode(cluster, event)
      ResourceEventKind.DELETED ->
        markNodeDeleted(cluster, event)
    }
  }

  private fun upsertNode(cluster: ClusterEntity, event: ResourceEventPayload): NodeEntity {
    val nodeName = event.resource.name
    val existingNode = nodeRepository.findByClusterIdAndName(cluster.id!!, nodeName)
    val isNew = existingNode == null

    val node = existingNode ?: NodeEntity().apply {
      this.cluster = cluster
      this.name = nodeName
      this.firstSeenAt = Instant.now()
    }

    node.uid = event.resource.uid
    node.labels = event.labels
    node.status = mapNodeStatus(event)
    node.lastUpdatedAt = Instant.now()
    node.deletedAt = null

    val saved = nodeRepository.save(node)
    log.info("Node {} in cluster {} (event: {})",
      if (isNew) "created" else "updated",
      nodeName, cluster.name, event.eventKind)

    broadcaster.broadcast(InfrastructureEvent(
      type = if (isNew) InfrastructureEventType.NODE_CREATED else InfrastructureEventType.NODE_UPDATED,
      clusterId = cluster.id!!,
      resourceId = saved.id!!,
      resourceName = nodeName
    ))

    return saved
  }

  private fun markNodeDeleted(cluster: ClusterEntity, event: ResourceEventPayload): NodeEntity? {
    val nodeName = event.resource.name
    val node = nodeRepository.findByClusterIdAndName(cluster.id!!, nodeName)
      ?: return null

    node.deletedAt = Instant.now()
    node.lastUpdatedAt = Instant.now()
    val saved = nodeRepository.save(node)

    log.info("Node marked as deleted: {} in cluster {}", nodeName, cluster.name)

    broadcaster.broadcast(InfrastructureEvent(
      type = InfrastructureEventType.NODE_DELETED,
      clusterId = cluster.id!!,
      resourceId = saved.id!!,
      resourceName = nodeName
    ))

    return saved
  }

  private fun mapNodeStatus(event: ResourceEventPayload): NodeStatus {
    val conditions = event.state?.conditions?.map { c ->
      NodeCondition(
        type = c.type,
        status = c.status,
        reason = c.reason,
        message = c.message
      )
    }

    val nodeMetadata = extractNodeMetadata(event.metadata)

    return NodeStatus(
      phase = event.state?.phase,
      conditions = conditions,
      capacity = nodeMetadata?.capacity,
      allocatable = nodeMetadata?.allocatable,
      nodeInfo = NodeInfo(
        kubeletVersion = nodeMetadata?.kubeletVersion,
        containerRuntimeVersion = nodeMetadata?.containerRuntimeVersion,
        osImage = nodeMetadata?.osImage,
        architecture = nodeMetadata?.architecture
      )
    )
  }

  @Suppress("UNCHECKED_CAST")
  private fun extractNodeMetadata(metadata: Map<String, Any?>?): NodeMetadata? {
    val nodeMap = metadata?.get("node") as? Map<String, Any?> ?: return null
    return NodeMetadata(
      kubeletVersion = nodeMap["kubeletVersion"] as? String,
      containerRuntimeVersion = nodeMap["containerRuntimeVersion"] as? String,
      osImage = nodeMap["osImage"] as? String,
      architecture = nodeMap["architecture"] as? String,
      capacity = nodeMap["capacity"] as? Map<String, String>,
      allocatable = nodeMap["allocatable"] as? Map<String, String>
    )
  }

  fun findActiveNodesByCluster(clusterId: Long): List<NodeEntity> {
    return nodeRepository.findActiveNodesByClusterId(clusterId)
  }

  fun findNodeByClusterAndName(clusterId: Long, name: String): NodeEntity? {
    return nodeRepository.findByClusterIdAndName(clusterId, name)
  }
}
