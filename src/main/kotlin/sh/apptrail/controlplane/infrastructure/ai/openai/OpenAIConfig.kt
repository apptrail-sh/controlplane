package sh.apptrail.controlplane.infrastructure.ai.openai

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean

@ConfigurationProperties(prefix = "apptrail.ai")
data class AIProperties(
  val enabled: Boolean = false,
  val model: String = "gpt-4o-mini",
  val maxTokens: Int = 2048,
  val temperature: Double = 0.7,
  val systemPrompt: String = """You are an AI assistant for AppTrail, a Kubernetes workload version tracking system.
You help users understand their deployment data, DORA metrics, and workload status across clusters.

When answering questions:
1. Use the available tools to fetch real data from the system
2. Present data in a clear, concise format
3. Highlight any concerning patterns (high failure rates, slow deployments, etc.)
4. Provide actionable insights when relevant

Available metrics you can access:
- Deployment frequency: How often deployments happen
- Lead time: How long deployments take
- Change failure rate: Percentage of failed deployments
- MTTR: Mean time to recovery from failures
- Workload status across environments and clusters
- Team performance comparisons"""
)

@AutoConfiguration(after = [OpenAiChatAutoConfiguration::class])
@ConditionalOnBean(OpenAiChatModel::class)
class OpenAIConfig(
  private val aiProperties: AIProperties
) {

  @Bean
  fun chatClient(chatModel: OpenAiChatModel): ChatClient {
    return ChatClient.builder(chatModel)
      .defaultSystem(aiProperties.systemPrompt)
      .build()
  }
}
