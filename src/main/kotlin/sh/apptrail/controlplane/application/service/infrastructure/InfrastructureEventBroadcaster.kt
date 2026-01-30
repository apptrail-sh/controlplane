package sh.apptrail.controlplane.application.service.infrastructure

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

enum class InfrastructureEventType {
  NODE_CREATED,
  NODE_UPDATED,
  NODE_DELETED,
  POD_CREATED,
  POD_UPDATED,
  POD_DELETED
}

data class InfrastructureEvent(
  val type: InfrastructureEventType,
  val clusterId: Long,
  val resourceId: Long,
  val resourceName: String,
  val namespace: String? = null,
  val data: Map<String, Any?> = emptyMap()
)

@Service
class InfrastructureEventBroadcaster {

  private val log = LoggerFactory.getLogger(javaClass)

  private val emittersByCluster = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

  fun addEmitter(clusterId: Long, emitter: SseEmitter) {
    val emitters = emittersByCluster.computeIfAbsent(clusterId) { CopyOnWriteArrayList() }
    emitters.add(emitter)
    log.debug("Added SSE emitter for cluster {}, total emitters: {}", clusterId, emitters.size)
  }

  fun removeEmitter(clusterId: Long, emitter: SseEmitter) {
    emittersByCluster[clusterId]?.remove(emitter)
    log.debug("Removed SSE emitter for cluster {}", clusterId)
  }

  fun broadcast(event: InfrastructureEvent) {
    val emitters = emittersByCluster[event.clusterId] ?: return

    if (emitters.isEmpty()) return

    log.debug("Broadcasting {} event for cluster {}, resource: {}",
      event.type, event.clusterId, event.resourceName)

    val deadEmitters = mutableListOf<SseEmitter>()

    emitters.forEach { emitter ->
      try {
        emitter.send(
          SseEmitter.event()
            .name(event.type.name.lowercase())
            .data(mapOf(
              "type" to event.type.name,
              "resourceId" to event.resourceId,
              "resourceName" to event.resourceName,
              "namespace" to event.namespace,
              "data" to event.data
            ))
        )
      } catch (e: Exception) {
        log.debug("Failed to send SSE event, removing emitter: {}", e.message)
        deadEmitters.add(emitter)
      }
    }

    deadEmitters.forEach { removeEmitter(event.clusterId, it) }
  }

  fun getEmitterCount(clusterId: Long): Int {
    return emittersByCluster[clusterId]?.size ?: 0
  }
}
