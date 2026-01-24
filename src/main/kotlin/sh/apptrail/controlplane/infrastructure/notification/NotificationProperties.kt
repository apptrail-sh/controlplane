package sh.apptrail.controlplane.infrastructure.notification

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "apptrail.notifications")
data class NotificationProperties(
  val enabled: Boolean = false,
  val frontendBaseUrl: String = "http://localhost:5173",
  val channels: List<NotificationChannelConfig> = emptyList(),
)

data class NotificationChannelConfig(
  val name: String,
  val type: ChannelType,
  val enabled: Boolean = true,
  val team: String? = null,
  val environments: List<String>? = null,
  val notificationTypes: List<NotificationType> = emptyList(),
  val slack: SlackChannelConfig? = null,
)

enum class ChannelType {
  SLACK,
  WEBHOOK
}

enum class NotificationType {
  DEPLOYMENT_STARTED,
  DEPLOYMENT_SUCCEEDED,
  DEPLOYMENT_FAILED
}

data class SlackChannelConfig(
  val webhookUrl: String,
  val channel: String? = null,
  val mentionOnFailure: String? = null,
)
