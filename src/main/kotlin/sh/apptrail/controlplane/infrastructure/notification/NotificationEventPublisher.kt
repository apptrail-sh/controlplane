package sh.apptrail.controlplane.infrastructure.notification

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import sh.apptrail.controlplane.infrastructure.notification.model.DeploymentNotification

@Component
class NotificationEventPublisher(
  private val eventPublisher: ApplicationEventPublisher,
  private val notificationProperties: NotificationProperties,
) {

  private val log = LoggerFactory.getLogger(NotificationEventPublisher::class.java)

  fun publish(notification: DeploymentNotification) {
    if (!notificationProperties.enabled) {
      return
    }

    log.debug(
      "Publishing deployment notification event: workload='{}', type='{}', version='{}'",
      notification.workloadName,
      notification.type,
      notification.currentVersion
    )

    eventPublisher.publishEvent(DeploymentNotificationEvent(notification))
  }
}

data class DeploymentNotificationEvent(
  val notification: DeploymentNotification,
)
