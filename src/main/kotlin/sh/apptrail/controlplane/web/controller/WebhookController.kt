package sh.apptrail.controlplane.web.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.service.events.HttpEventsService

@RestController
@RequestMapping("/api/events/http")
class WebhookController(
  private val httpEventsService: HttpEventsService,
) {
  @PostMapping("/events")
  fun ingestEvent(@RequestBody payload: Map<String, Any>): ResponseEntity<Void> {
    httpEventsService.processEvent(payload)
    return ResponseEntity.accepted().build()
  }
}
