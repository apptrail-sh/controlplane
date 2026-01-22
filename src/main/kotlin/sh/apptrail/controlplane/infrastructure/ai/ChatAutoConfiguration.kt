package sh.apptrail.controlplane.infrastructure.ai

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.ai.chat.client.ChatClient
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import sh.apptrail.controlplane.application.service.chat.ChatService
import sh.apptrail.controlplane.application.service.chat.ConversationMemory
import sh.apptrail.controlplane.infrastructure.ai.openai.OpenAIConfig
import sh.apptrail.controlplane.web.controller.ChatController

@AutoConfiguration(after = [OpenAIConfig::class])
@ConditionalOnBean(ChatClient::class)
class ChatAutoConfiguration {

  @Bean
  fun chatService(
    chatClient: ChatClient,
    conversationMemory: ConversationMemory,
    objectMapper: ObjectMapper
  ): ChatService {
    return ChatService(chatClient, conversationMemory, objectMapper)
  }

  @Bean
  fun chatController(
    chatService: ChatService,
    objectMapper: ObjectMapper
  ): ChatController {
    return ChatController(chatService, objectMapper)
  }
}
