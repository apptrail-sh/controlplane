package sh.apptrail.controlplane.application.service.infrastructure

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sh.apptrail.controlplane.application.model.infrastructure.*
import sh.apptrail.controlplane.application.service.ClusterService
import sh.apptrail.controlplane.infrastructure.persistence.entity.*
import sh.apptrail.controlplane.infrastructure.persistence.repository.*
import java.time.Instant

@Service
class PodService(
  private val podRepository: PodRepository,
  private val nodeRepository: NodeRepository,
  private val clusterService: ClusterService,
  private val workloadInstanceRepository: WorkloadInstanceRepository,
  private val broadcaster: InfrastructureEventBroadcaster
) {

  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  fun processPodEvent(event: ResourceEventPayload): PodEntity? {
    val clusterId = event.source.clusterId
    val cluster = clusterService.findOrCreateCluster(clusterId)

    return when (event.eventKind) {
      ResourceEventKind.CREATED, ResourceEventKind.UPDATED, ResourceEventKind.STATUS_CHANGE ->
        upsertPod(cluster, event)
      ResourceEventKind.DELETED ->
        markPodDeleted(cluster, event)
    }
  }

  private fun upsertPod(cluster: ClusterEntity, event: ResourceEventPayload): PodEntity {
    val namespace = event.resource.namespace ?: ""
    val podName = event.resource.name
    val existingPod = podRepository.findByClusterIdAndNamespaceAndName(cluster.id!!, namespace, podName)
    val isNew = existingPod == null

    val pod = existingPod ?: PodEntity().apply {
      this.cluster = cluster
      this.namespace = namespace
      this.name = podName
      this.firstSeenAt = Instant.now()
    }

    pod.uid = event.resource.uid
    pod.labels = event.labels
    pod.status = mapPodStatus(event)
    pod.lastUpdatedAt = Instant.now()
    pod.deletedAt = null

    val podMetadata = extractPodMetadata(event.metadata)

    pod.node = podMetadata?.nodeName?.let { nodeName ->
      nodeRepository.findByClusterIdAndName(cluster.id!!, nodeName)
    }

    pod.workloadInstance = resolveWorkloadInstance(cluster, namespace, podMetadata)

    val saved = podRepository.save(pod)
    log.debug("Pod {} in cluster {}/{} (event: {})",
      if (isNew) "created" else "updated",
      podName, cluster.name, namespace, event.eventKind)

    broadcaster.broadcast(InfrastructureEvent(
      type = if (isNew) InfrastructureEventType.POD_CREATED else InfrastructureEventType.POD_UPDATED,
      clusterId = cluster.id!!,
      resourceId = saved.id!!,
      resourceName = podName,
      namespace = namespace
    ))

    return saved
  }

  private fun markPodDeleted(cluster: ClusterEntity, event: ResourceEventPayload): PodEntity? {
    val namespace = event.resource.namespace ?: ""
    val podName = event.resource.name
    val pod = podRepository.findByClusterIdAndNamespaceAndName(cluster.id!!, namespace, podName)
      ?: return null

    pod.deletedAt = Instant.now()
    pod.lastUpdatedAt = Instant.now()
    val saved = podRepository.save(pod)

    log.debug("Pod marked as deleted: {}/{}/{}", cluster.name, namespace, podName)

    broadcaster.broadcast(InfrastructureEvent(
      type = InfrastructureEventType.POD_DELETED,
      clusterId = cluster.id!!,
      resourceId = saved.id!!,
      resourceName = podName,
      namespace = namespace
    ))

    return saved
  }

  private fun mapPodStatus(event: ResourceEventPayload): PodStatus {
    val conditions = event.state?.conditions?.map { c ->
      PodCondition(
        type = c.type,
        status = c.status,
        reason = c.reason,
        message = c.message
      )
    }

    val podMetadata = extractPodMetadata(event.metadata)

    return PodStatus(
      phase = event.state?.phase,
      conditions = conditions,
      podIP = podMetadata?.podIP,
      startTime = podMetadata?.startTime,
      containerStatuses = podMetadata?.containers?.map { c ->
        ContainerStatus(
          name = c.name,
          image = c.image,
          ready = c.ready,
          restartCount = c.restartCount,
          state = c.state,
          reason = c.reason,
          message = c.message
        )
      },
      initContainerStatuses = podMetadata?.initContainers?.map { c ->
        ContainerStatus(
          name = c.name,
          image = c.image,
          ready = c.ready,
          restartCount = c.restartCount,
          state = c.state,
          reason = c.reason,
          message = c.message
        )
      }
    )
  }

  @Suppress("UNCHECKED_CAST")
  private fun extractPodMetadata(metadata: Map<String, Any?>?): PodMetadata? {
    val podMap = metadata?.get("pod") as? Map<String, Any?> ?: return null

    val containers = (podMap["containers"] as? List<Map<String, Any?>>)?.map { c ->
      ContainerStatusInfo(
        name = c["name"] as? String ?: "",
        image = c["image"] as? String,
        ready = c["ready"] as? Boolean ?: false,
        restartCount = (c["restartCount"] as? Number)?.toInt() ?: 0,
        state = c["state"] as? String,
        reason = c["reason"] as? String,
        message = c["message"] as? String
      )
    }

    val initContainers = (podMap["initContainers"] as? List<Map<String, Any?>>)?.map { c ->
      ContainerStatusInfo(
        name = c["name"] as? String ?: "",
        image = c["image"] as? String,
        ready = c["ready"] as? Boolean ?: false,
        restartCount = (c["restartCount"] as? Number)?.toInt() ?: 0,
        state = c["state"] as? String,
        reason = c["reason"] as? String,
        message = c["message"] as? String
      )
    }

    val startTimeStr = podMap["startTime"] as? String
    val startTime = startTimeStr?.let {
      try { Instant.parse(it) } catch (e: Exception) { null }
    }

    return PodMetadata(
      ownerKind = podMap["ownerKind"] as? String,
      ownerName = podMap["ownerName"] as? String,
      ownerUID = podMap["ownerUID"] as? String,
      nodeName = podMap["nodeName"] as? String,
      podIP = podMap["podIP"] as? String,
      startTime = startTime,
      restartCount = (podMap["restartCount"] as? Number)?.toInt() ?: 0,
      containers = containers,
      initContainers = initContainers
    )
  }

  private fun resolveWorkloadInstance(
    cluster: ClusterEntity,
    namespace: String,
    podMetadata: PodMetadata?
  ): WorkloadInstanceEntity? {
    val ownerKind = podMetadata?.ownerKind ?: return null
    val ownerName = podMetadata.ownerName ?: return null

    val workloadName = when (ownerKind) {
      "ReplicaSet" -> extractDeploymentNameFromReplicaSet(ownerName)
      "StatefulSet", "DaemonSet", "Deployment" -> ownerName
      else -> return null
    }

    return workloadInstanceRepository.findByWorkloadNameAndClusterIdAndNamespace(
      workloadName,
      cluster.id!!,
      namespace
    )
  }

  private fun extractDeploymentNameFromReplicaSet(replicaSetName: String): String {
    val lastDashIndex = replicaSetName.lastIndexOf('-')
    return if (lastDashIndex > 0) {
      replicaSetName.substring(0, lastDashIndex)
    } else {
      replicaSetName
    }
  }

  fun findActivePodsByWorkloadInstance(workloadInstanceId: Long): List<PodEntity> {
    return podRepository.findActivePodsByWorkloadInstance(workloadInstanceId)
  }

  fun findActivePodsByNode(nodeId: Long): List<PodEntity> {
    return podRepository.findActivePodsByNode(nodeId)
  }

  fun findActivePodsInNamespace(clusterId: Long, namespace: String): List<PodEntity> {
    return podRepository.findActivePodsInNamespace(clusterId, namespace)
  }
}
