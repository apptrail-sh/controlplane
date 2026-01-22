package sh.apptrail.controlplane.application.service.chat

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class ConversationEntry(
  val role: String,
  val content: String,
  val timestamp: Instant = Instant.now()
)

data class Conversation(
  val sessionId: String,
  val entries: MutableList<ConversationEntry> = mutableListOf(),
  var lastAccessedAt: Instant = Instant.now(),
  val maxEntries: Int = 20 // Store last 10 exchanges (20 messages)
)

@Component
class ConversationMemory {
  private val conversations = ConcurrentHashMap<String, Conversation>()
  private val sessionTimeoutMinutes = 30L

  fun getOrCreateConversation(sessionId: String): Conversation {
    cleanupExpiredSessions()
    return conversations.computeIfAbsent(sessionId) { Conversation(sessionId) }
      .also { it.lastAccessedAt = Instant.now() }
  }

  fun addUserMessage(sessionId: String, content: String) {
    val conversation = getOrCreateConversation(sessionId)
    conversation.entries.add(ConversationEntry(role = "user", content = content))
    trimConversation(conversation)
  }

  fun addAssistantMessage(sessionId: String, content: String) {
    val conversation = getOrCreateConversation(sessionId)
    conversation.entries.add(ConversationEntry(role = "assistant", content = content))
    trimConversation(conversation)
  }

  fun getMessages(sessionId: String): List<Message> {
    val conversation = conversations[sessionId] ?: return emptyList()
    return conversation.entries.map { entry ->
      when (entry.role) {
        "user" -> UserMessage(entry.content)
        "assistant" -> AssistantMessage(entry.content)
        else -> UserMessage(entry.content)
      }
    }
  }

  fun clearConversation(sessionId: String) {
    conversations.remove(sessionId)
  }

  private fun trimConversation(conversation: Conversation) {
    while (conversation.entries.size > conversation.maxEntries) {
      conversation.entries.removeAt(0)
    }
  }

  private fun cleanupExpiredSessions() {
    val cutoff = Instant.now().minusSeconds(sessionTimeoutMinutes * 60)
    conversations.entries.removeIf { it.value.lastAccessedAt.isBefore(cutoff) }
  }
}
