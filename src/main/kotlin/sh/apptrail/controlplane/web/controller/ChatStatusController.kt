package sh.apptrail.controlplane.web.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.application.service.chat.ChatService
import sh.apptrail.controlplane.infrastructure.ai.openai.AIProperties

@RestController
@RequestMapping("/api/v1/chat")
class ChatStatusController(
  private val aiProperties: AIProperties,
  private val chatService: ChatService?
) {

  @GetMapping("/status")
  fun getStatus(): ResponseEntity<Map<String, Any?>> {
    val enabled = aiProperties.enabled && chatService != null

    return ResponseEntity.ok(mapOf(
      "enabled" to enabled,
      "provider" to if (enabled) "openai" else null
    ))
  }
}
