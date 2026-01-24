package sh.apptrail.controlplane.infrastructure.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.notification.channel.NotificationChannel
import sh.apptrail.controlplane.infrastructure.notification.model.DeploymentNotification

@Service
class NotificationService(
  private val notificationProperties: NotificationProperties,
  private val notificationChannels: List<NotificationChannel>,
) {

  private val log = LoggerFactory.getLogger(NotificationService::class.java)

  fun sendNotification(notification: DeploymentNotification) {
    if (!notificationProperties.enabled) {
      log.debug("Notifications are disabled, skipping notification for workload '{}'", notification.workloadName)
      return
    }

    val matchingChannels = notificationProperties.channels.filter { channel ->
      matchesChannel(notification, channel)
    }

    if (matchingChannels.isEmpty()) {
      log.debug(
        "No matching channels for notification: workload='{}', type='{}', team='{}', environment='{}'",
        notification.workloadName,
        notification.type,
        notification.team,
        notification.environment
      )
      return
    }

    for (channelConfig in matchingChannels) {
      sendToChannel(notification, channelConfig)
    }
  }

  private fun matchesChannel(notification: DeploymentNotification, config: NotificationChannelConfig): Boolean {
    if (!config.enabled) {
      return false
    }

    // Check notification type
    if (config.notificationTypes.isNotEmpty() && notification.type !in config.notificationTypes) {
      return false
    }

    // Check team filter
    if (config.team != null && config.team != notification.team) {
      return false
    }

    // Check environment filter
    if (config.environments != null && config.environments.isNotEmpty()) {
      if (notification.environment !in config.environments) {
        return false
      }
    }

    return true
  }

  private fun sendToChannel(notification: DeploymentNotification, config: NotificationChannelConfig) {
    val channel = notificationChannels.find { it.supports(config) }

    if (channel == null) {
      log.warn("No notification channel implementation found for channel '{}' of type '{}'", config.name, config.type)
      return
    }

    try {
      channel.send(notification, config, notificationProperties.frontendBaseUrl)
      log.info(
        "Successfully sent notification to channel '{}' for workload '{}'",
        config.name,
        notification.workloadName
      )
    } catch (e: Exception) {
      log.error(
        "Failed to send notification to channel '{}' for workload '{}': {}",
        config.name,
        notification.workloadName,
        e.message,
        e
      )
    }
  }
}
