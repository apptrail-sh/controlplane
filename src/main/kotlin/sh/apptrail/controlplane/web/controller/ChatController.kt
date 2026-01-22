package sh.apptrail.controlplane.web.controller

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import sh.apptrail.controlplane.application.service.chat.ChatService
import sh.apptrail.controlplane.web.dto.ChatRequest
import sh.apptrail.controlplane.web.dto.ChatResponse
import java.util.*

@RestController
@RequestMapping("/api/v1/chat")
class ChatController(
  private val chatService: ChatService,
  private val objectMapper: ObjectMapper
) {

  @PostMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
  fun streamChat(
    @Valid @RequestBody request: ChatRequest,
    @RequestHeader("X-Session-Id", required = false) sessionId: String?
  ): Flux<ServerSentEvent<String>> {
    val effectiveSessionId = sessionId ?: UUID.randomUUID().toString()

    return chatService.streamChat(effectiveSessionId, request.message)
      .map { event ->
        ServerSentEvent.builder<String>()
          .data(objectMapper.writeValueAsString(event))
          .build()
      }
  }

  @PostMapping("/sync")
  fun chat(
    @Valid @RequestBody request: ChatRequest,
    @RequestHeader("X-Session-Id", required = false) sessionId: String?
  ): ResponseEntity<ChatResponse> {
    val effectiveSessionId = sessionId ?: UUID.randomUUID().toString()

    val response = chatService.chat(effectiveSessionId, request.message)

    return ResponseEntity.ok(ChatResponse(
      message = response,
      sessionId = effectiveSessionId
    ))
  }

  @DeleteMapping("/history")
  fun clearHistory(
    @RequestHeader("X-Session-Id") sessionId: String
  ): ResponseEntity<Map<String, String>> {
    chatService.clearHistory(sessionId)
    return ResponseEntity.ok(mapOf("status" to "cleared"))
  }

}
