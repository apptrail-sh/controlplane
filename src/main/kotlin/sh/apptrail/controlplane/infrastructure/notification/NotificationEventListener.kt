package sh.apptrail.controlplane.infrastructure.notification

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class NotificationEventListener(
  private val notificationService: NotificationService,
) {

  private val log = LoggerFactory.getLogger(NotificationEventListener::class.java)

  @Async("notificationExecutor")
  @EventListener
  fun handleDeploymentNotificationEvent(event: DeploymentNotificationEvent) {
    log.debug(
      "Handling deployment notification event: workload='{}', type='{}'",
      event.notification.workloadName,
      event.notification.type
    )

    try {
      notificationService.sendNotification(event.notification)
    } catch (e: Exception) {
      log.error(
        "Unhandled error processing notification for workload '{}': {}",
        event.notification.workloadName,
        e.message,
        e
      )
    }
  }
}
