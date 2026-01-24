package sh.apptrail.controlplane.infrastructure.notification.channel

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import sh.apptrail.controlplane.infrastructure.notification.ChannelType
import sh.apptrail.controlplane.infrastructure.notification.NotificationChannelConfig
import sh.apptrail.controlplane.infrastructure.notification.model.DeploymentNotification
import sh.apptrail.controlplane.infrastructure.notification.slack.SlackClient
import sh.apptrail.controlplane.infrastructure.notification.slack.SlackMessageBuilder

@Component
class SlackNotificationChannel(
  private val slackClient: SlackClient,
) : NotificationChannel {

  private val log = LoggerFactory.getLogger(SlackNotificationChannel::class.java)

  override fun supports(config: NotificationChannelConfig): Boolean {
    return config.type == ChannelType.SLACK && config.slack != null
  }

  override fun send(notification: DeploymentNotification, config: NotificationChannelConfig, frontendBaseUrl: String) {
    val slackConfig = config.slack
      ?: throw IllegalArgumentException("Slack config is required for Slack channel")

    if (slackConfig.webhookUrl.isBlank()) {
      log.warn("Slack webhook URL is empty for channel '${config.name}', skipping notification")
      return
    }

    val payload = SlackMessageBuilder.buildMessage(notification, frontendBaseUrl, slackConfig)

    log.info(
      "Sending Slack notification for workload '{}' to channel '{}'",
      notification.workloadName,
      config.name
    )

    slackClient.sendMessage(slackConfig.webhookUrl, payload)
  }
}
