package sh.apptrail.controlplane.application.service.chat

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import reactor.core.publisher.Flux
import sh.apptrail.controlplane.web.dto.ChatStreamEvent
import sh.apptrail.controlplane.web.dto.ChatStreamEventType

class ChatService(
  private val chatClient: ChatClient,
  private val conversationMemory: ConversationMemory,
  private val objectMapper: ObjectMapper
) {
  private val log = LoggerFactory.getLogger(ChatService::class.java)

  fun streamChat(sessionId: String, userMessage: String): Flux<ChatStreamEvent> {
    return Flux.create { sink ->
      try {
        // Add user message to conversation memory
        conversationMemory.addUserMessage(sessionId, userMessage)

        // Get conversation history for context
        val history = conversationMemory.getMessages(sessionId)

        // Build prompt with history
        val contextPrompt = buildContextPrompt(history, userMessage)

        val responseBuilder = StringBuilder()

        val response = chatClient.prompt()
          .user(contextPrompt)
          .stream()
          .content()

        response.doOnNext { chunk ->
          responseBuilder.append(chunk)
          sink.next(ChatStreamEvent(
            type = ChatStreamEventType.TEXT,
            content = chunk
          ))
        }.doOnComplete {
          val fullResponse = responseBuilder.toString()
          conversationMemory.addAssistantMessage(sessionId, fullResponse)

          sink.next(ChatStreamEvent(
            type = ChatStreamEventType.COMPLETE,
            content = null
          ))
          sink.complete()
        }.doOnError { error ->
          log.error("Error in chat stream", error)
          sink.next(ChatStreamEvent(
            type = ChatStreamEventType.ERROR,
            content = error.message ?: "An error occurred"
          ))
          sink.complete()
        }.subscribe()
      } catch (e: Exception) {
        log.error("Error starting chat stream", e)
        sink.next(ChatStreamEvent(
          type = ChatStreamEventType.ERROR,
          content = e.message ?: "Failed to start chat"
        ))
        sink.complete()
      }
    }
  }

  fun chat(sessionId: String, userMessage: String): String {
    try {
      // Add user message to conversation memory
      conversationMemory.addUserMessage(sessionId, userMessage)

      // Get conversation history for context
      val history = conversationMemory.getMessages(sessionId)

      // Build prompt with history
      val contextPrompt = buildContextPrompt(history, userMessage)

      val response = chatClient.prompt()
        .user(contextPrompt)
        .call()
        .content() ?: "I couldn't generate a response."

      // Store the response in conversation memory
      conversationMemory.addAssistantMessage(sessionId, response)

      return response
    } catch (e: Exception) {
      log.error("Error in chat", e)
      throw e
    }
  }

  fun clearHistory(sessionId: String) {
    conversationMemory.clearConversation(sessionId)
  }

  private fun buildContextPrompt(
    history: List<org.springframework.ai.chat.messages.Message>,
    currentMessage: String
  ): String {
    if (history.isEmpty()) {
      return currentMessage
    }

    val historyText = history.dropLast(1).joinToString("\n") { msg ->
      when (msg) {
        is org.springframework.ai.chat.messages.UserMessage -> "User: ${msg.text}"
        is org.springframework.ai.chat.messages.AssistantMessage -> "Assistant: ${msg.text}"
        else -> ""
      }
    }

    return """Previous conversation:
$historyText

Current question: $currentMessage"""
  }
}
