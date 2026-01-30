package sh.apptrail.controlplane.web.controller

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import sh.apptrail.controlplane.application.service.infrastructure.InfrastructureEventBroadcaster
import java.util.concurrent.CopyOnWriteArrayList

@RestController
@RequestMapping("/api/v1/clusters/{clusterId}/infrastructure")
class InfrastructureStreamController(
  private val broadcaster: InfrastructureEventBroadcaster
) {

  @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
  fun streamInfrastructureEvents(@PathVariable clusterId: Long): SseEmitter {
    val emitter = SseEmitter(0L) // No timeout

    broadcaster.addEmitter(clusterId, emitter)

    emitter.onCompletion {
      broadcaster.removeEmitter(clusterId, emitter)
    }
    emitter.onTimeout {
      broadcaster.removeEmitter(clusterId, emitter)
    }
    emitter.onError {
      broadcaster.removeEmitter(clusterId, emitter)
    }

    return emitter
  }
}
