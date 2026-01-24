package sh.apptrail.controlplane.infrastructure.notification

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import sh.apptrail.controlplane.infrastructure.notification.channel.NotificationChannel
import sh.apptrail.controlplane.infrastructure.notification.model.DeploymentNotification
import java.time.Instant

class NotificationServiceTests {

  private lateinit var mockChannel: NotificationChannel
  private lateinit var notificationProperties: NotificationProperties
  private lateinit var notificationService: NotificationService

  private val baseNotification = DeploymentNotification(
    type = NotificationType.DEPLOYMENT_SUCCEEDED,
    workloadId = 123L,
    workloadName = "my-service",
    workloadKind = "Deployment",
    team = "platform",
    environment = "production",
    cluster = "prod-us-east-1",
    namespace = "default",
    currentVersion = "v1.2.4",
    previousVersion = "v1.2.3",
    occurredAt = Instant.now(),
    deploymentDurationSeconds = 154,
    errorMessage = null,
  )

  @BeforeEach
  fun setUp() {
    mockChannel = mock<NotificationChannel>()
    whenever(mockChannel.supports(any())).thenReturn(true)
  }

  @Test
  fun `sendNotification does nothing when notifications are disabled`() {
    notificationProperties = NotificationProperties(
      enabled = false,
      frontendBaseUrl = "https://apptrail.example.com",
      channels = listOf(
        NotificationChannelConfig(
          name = "test",
          type = ChannelType.SLACK,
          enabled = true,
          notificationTypes = listOf(NotificationType.DEPLOYMENT_SUCCEEDED),
          slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test")
        )
      )
    )
    notificationService = NotificationService(notificationProperties, listOf(mockChannel))

    notificationService.sendNotification(baseNotification)

    verify(mockChannel, never()).send(any(), any(), any())
  }

  @Test
  fun `sendNotification sends to matching channels`() {
    notificationProperties = NotificationProperties(
      enabled = true,
      frontendBaseUrl = "https://apptrail.example.com",
      channels = listOf(
        NotificationChannelConfig(
          name = "test",
          type = ChannelType.SLACK,
          enabled = true,
          notificationTypes = listOf(NotificationType.DEPLOYMENT_SUCCEEDED),
          slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test")
        )
      )
    )
    notificationService = NotificationService(notificationProperties, listOf(mockChannel))

    notificationService.sendNotification(baseNotification)

    verify(mockChannel).send(eq(baseNotification), any(), eq("https://apptrail.example.com"))
  }

  @Test
  fun `sendNotification filters by notification type`() {
    notificationProperties = NotificationProperties(
      enabled = true,
      frontendBaseUrl = "https://apptrail.example.com",
      channels = listOf(
        NotificationChannelConfig(
          name = "failures-only",
          type = ChannelType.SLACK,
          enabled = true,
          notificationTypes = listOf(NotificationType.DEPLOYMENT_FAILED),
          slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test")
        )
      )
    )
    notificationService = NotificationService(notificationProperties, listOf(mockChannel))

    notificationService.sendNotification(baseNotification) // SUCCESS notification

    verify(mockChannel, never()).send(any(), any(), any())
  }

  @Test
  fun `sendNotification filters by team`() {
    notificationProperties = NotificationProperties(
      enabled = true,
      frontendBaseUrl = "https://apptrail.example.com",
      channels = listOf(
        NotificationChannelConfig(
          name = "team1-only",
          type = ChannelType.SLACK,
          enabled = true,
          team = "team1",
          notificationTypes = listOf(NotificationType.DEPLOYMENT_SUCCEEDED),
          slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test")
        )
      )
    )
    notificationService = NotificationService(notificationProperties, listOf(mockChannel))

    // Notification is for "platform" team, channel is for "team1"
    notificationService.sendNotification(baseNotification)

    verify(mockChannel, never()).send(any(), any(), any())
  }

  @Test
  fun `sendNotification matches when team filter matches`() {
    notificationProperties = NotificationProperties(
      enabled = true,
      frontendBaseUrl = "https://apptrail.example.com",
      channels = listOf(
        NotificationChannelConfig(
          name = "platform-team",
          type = ChannelType.SLACK,
          enabled = true,
          team = "platform",
          notificationTypes = listOf(NotificationType.DEPLOYMENT_SUCCEEDED),
          slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test")
        )
      )
    )
    notificationService = NotificationService(notificationProperties, listOf(mockChannel))

    notificationService.sendNotification(baseNotification)

    verify(mockChannel).send(eq(baseNotification), any(), any())
  }

  @Test
  fun `sendNotification filters by environment`() {
    notificationProperties = NotificationProperties(
      enabled = true,
      frontendBaseUrl = "https://apptrail.example.com",
      channels = listOf(
        NotificationChannelConfig(
          name = "staging-only",
          type = ChannelType.SLACK,
          enabled = true,
          environments = listOf("staging"),
          notificationTypes = listOf(NotificationType.DEPLOYMENT_SUCCEEDED),
          slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test")
        )
      )
    )
    notificationService = NotificationService(notificationProperties, listOf(mockChannel))

    // Notification is for "production" environment, channel is for "staging"
    notificationService.sendNotification(baseNotification)

    verify(mockChannel, never()).send(any(), any(), any())
  }

  @Test
  fun `sendNotification matches when environment filter matches`() {
    notificationProperties = NotificationProperties(
      enabled = true,
      frontendBaseUrl = "https://apptrail.example.com",
      channels = listOf(
        NotificationChannelConfig(
          name = "production-channel",
          type = ChannelType.SLACK,
          enabled = true,
          environments = listOf("production"),
          notificationTypes = listOf(NotificationType.DEPLOYMENT_SUCCEEDED),
          slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test")
        )
      )
    )
    notificationService = NotificationService(notificationProperties, listOf(mockChannel))

    notificationService.sendNotification(baseNotification)

    verify(mockChannel).send(eq(baseNotification), any(), any())
  }

  @Test
  fun `sendNotification matches channel with null team filter`() {
    notificationProperties = NotificationProperties(
      enabled = true,
      frontendBaseUrl = "https://apptrail.example.com",
      channels = listOf(
        NotificationChannelConfig(
          name = "all-teams",
          type = ChannelType.SLACK,
          enabled = true,
          team = null, // No team filter
          notificationTypes = listOf(NotificationType.DEPLOYMENT_SUCCEEDED),
          slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test")
        )
      )
    )
    notificationService = NotificationService(notificationProperties, listOf(mockChannel))

    notificationService.sendNotification(baseNotification)

    verify(mockChannel).send(eq(baseNotification), any(), any())
  }

  @Test
  fun `sendNotification matches channel with null environments filter`() {
    notificationProperties = NotificationProperties(
      enabled = true,
      frontendBaseUrl = "https://apptrail.example.com",
      channels = listOf(
        NotificationChannelConfig(
          name = "all-environments",
          type = ChannelType.SLACK,
          enabled = true,
          environments = null, // No environment filter
          notificationTypes = listOf(NotificationType.DEPLOYMENT_SUCCEEDED),
          slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test")
        )
      )
    )
    notificationService = NotificationService(notificationProperties, listOf(mockChannel))

    notificationService.sendNotification(baseNotification)

    verify(mockChannel).send(eq(baseNotification), any(), any())
  }

  @Test
  fun `sendNotification skips disabled channels`() {
    notificationProperties = NotificationProperties(
      enabled = true,
      frontendBaseUrl = "https://apptrail.example.com",
      channels = listOf(
        NotificationChannelConfig(
          name = "disabled-channel",
          type = ChannelType.SLACK,
          enabled = false, // Disabled
          notificationTypes = listOf(NotificationType.DEPLOYMENT_SUCCEEDED),
          slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test")
        )
      )
    )
    notificationService = NotificationService(notificationProperties, listOf(mockChannel))

    notificationService.sendNotification(baseNotification)

    verify(mockChannel, never()).send(any(), any(), any())
  }

  @Test
  fun `sendNotification sends to multiple matching channels`() {
    notificationProperties = NotificationProperties(
      enabled = true,
      frontendBaseUrl = "https://apptrail.example.com",
      channels = listOf(
        NotificationChannelConfig(
          name = "channel1",
          type = ChannelType.SLACK,
          enabled = true,
          notificationTypes = listOf(NotificationType.DEPLOYMENT_SUCCEEDED),
          slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test1")
        ),
        NotificationChannelConfig(
          name = "channel2",
          type = ChannelType.SLACK,
          enabled = true,
          notificationTypes = listOf(NotificationType.DEPLOYMENT_SUCCEEDED),
          slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test2")
        )
      )
    )
    notificationService = NotificationService(notificationProperties, listOf(mockChannel))

    notificationService.sendNotification(baseNotification)

    verify(mockChannel, times(2)).send(eq(baseNotification), any(), any())
  }

  @Test
  fun `sendNotification continues on channel failure`() {
    val failingChannel = mock<NotificationChannel>()
    whenever(failingChannel.supports(any())).thenReturn(true)
    whenever(failingChannel.send(any(), any(), any())).thenThrow(RuntimeException("Slack API error"))

    notificationProperties = NotificationProperties(
      enabled = true,
      frontendBaseUrl = "https://apptrail.example.com",
      channels = listOf(
        NotificationChannelConfig(
          name = "failing-channel",
          type = ChannelType.SLACK,
          enabled = true,
          notificationTypes = listOf(NotificationType.DEPLOYMENT_SUCCEEDED),
          slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test")
        )
      )
    )
    notificationService = NotificationService(notificationProperties, listOf(failingChannel))

    // Should not throw
    assertDoesNotThrow {
      notificationService.sendNotification(baseNotification)
    }
  }

  @Test
  fun `sendNotification matches all notification types when empty`() {
    notificationProperties = NotificationProperties(
      enabled = true,
      frontendBaseUrl = "https://apptrail.example.com",
      channels = listOf(
        NotificationChannelConfig(
          name = "all-types",
          type = ChannelType.SLACK,
          enabled = true,
          notificationTypes = emptyList(), // Empty = all types
          slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test")
        )
      )
    )
    notificationService = NotificationService(notificationProperties, listOf(mockChannel))

    notificationService.sendNotification(baseNotification)

    verify(mockChannel).send(eq(baseNotification), any(), any())
  }
}
