package sh.apptrail.controlplane.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ChatRequest(
  @field:NotBlank(message = "Message cannot be blank")
  @field:Size(max = 1000, message = "Message cannot exceed 1000 characters")
  val message: String
)

data class ChatResponse(
  val message: String,
  val sessionId: String,
  val data: Any? = null
)

enum class ChatStreamEventType {
  TEXT,
  DATA,
  THINKING,
  ERROR,
  COMPLETE
}

data class ChatStreamEvent(
  val type: ChatStreamEventType,
  val content: String? = null,
  val data: Any? = null
)
