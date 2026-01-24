package sh.apptrail.controlplane.infrastructure.notification.channel

import sh.apptrail.controlplane.infrastructure.notification.NotificationChannelConfig
import sh.apptrail.controlplane.infrastructure.notification.model.DeploymentNotification

interface NotificationChannel {

  fun supports(config: NotificationChannelConfig): Boolean

  fun send(notification: DeploymentNotification, config: NotificationChannelConfig, frontendBaseUrl: String)
}
