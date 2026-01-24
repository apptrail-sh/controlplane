package sh.apptrail.controlplane.infrastructure.notification.channel

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import sh.apptrail.controlplane.infrastructure.notification.ChannelType
import sh.apptrail.controlplane.infrastructure.notification.NotificationChannelConfig
import sh.apptrail.controlplane.infrastructure.notification.NotificationType
import sh.apptrail.controlplane.infrastructure.notification.SlackChannelConfig
import sh.apptrail.controlplane.infrastructure.notification.model.DeploymentNotification
import sh.apptrail.controlplane.infrastructure.notification.slack.SlackClient
import java.time.Instant

class SlackNotificationChannelTests {

  private lateinit var slackClient: SlackClient
  private lateinit var slackNotificationChannel: SlackNotificationChannel

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
    slackClient = mock<SlackClient>()
    slackNotificationChannel = SlackNotificationChannel(slackClient)
  }

  @Test
  fun `supports returns true for SLACK channel type with slack config`() {
    val config = NotificationChannelConfig(
      name = "test",
      type = ChannelType.SLACK,
      enabled = true,
      slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test")
    )

    assertTrue(slackNotificationChannel.supports(config))
  }

  @Test
  fun `supports returns false for SLACK channel type without slack config`() {
    val config = NotificationChannelConfig(
      name = "test",
      type = ChannelType.SLACK,
      enabled = true,
      slack = null
    )

    assertFalse(slackNotificationChannel.supports(config))
  }

  @Test
  fun `supports returns false for WEBHOOK channel type`() {
    val config = NotificationChannelConfig(
      name = "test",
      type = ChannelType.WEBHOOK,
      enabled = true,
      slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test")
    )

    assertFalse(slackNotificationChannel.supports(config))
  }

  @Test
  fun `send calls SlackClient with correct webhook URL`() {
    val config = NotificationChannelConfig(
      name = "test",
      type = ChannelType.SLACK,
      enabled = true,
      slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/services/xxx/yyy/zzz")
    )

    slackNotificationChannel.send(baseNotification, config, "https://apptrail.example.com")

    verify(slackClient).sendMessage(
      eq("https://hooks.slack.com/services/xxx/yyy/zzz"),
      any()
    )
  }

  @Test
  fun `send builds correct payload structure`() {
    val config = NotificationChannelConfig(
      name = "test",
      type = ChannelType.SLACK,
      enabled = true,
      slack = SlackChannelConfig(webhookUrl = "https://hooks.slack.com/test")
    )

    val payloadCaptor = argumentCaptor<Map<String, Any>>()

    slackNotificationChannel.send(baseNotification, config, "https://apptrail.example.com")

    verify(slackClient).sendMessage(any(), payloadCaptor.capture())

    val payload = payloadCaptor.firstValue
    assertTrue(payload.containsKey("blocks"))
  }

  @Test
  fun `send throws when slack config is null`() {
    val config = NotificationChannelConfig(
      name = "test",
      type = ChannelType.SLACK,
      enabled = true,
      slack = null
    )

    assertThrows(IllegalArgumentException::class.java) {
      slackNotificationChannel.send(baseNotification, config, "https://apptrail.example.com")
    }
  }

  @Test
  fun `send skips notification when webhook URL is blank`() {
    val config = NotificationChannelConfig(
      name = "test",
      type = ChannelType.SLACK,
      enabled = true,
      slack = SlackChannelConfig(webhookUrl = "")
    )

    slackNotificationChannel.send(baseNotification, config, "https://apptrail.example.com")

    verify(slackClient, never()).sendMessage(any(), any())
  }

  @Test
  fun `send skips notification when webhook URL is only whitespace`() {
    val config = NotificationChannelConfig(
      name = "test",
      type = ChannelType.SLACK,
      enabled = true,
      slack = SlackChannelConfig(webhookUrl = "   ")
    )

    slackNotificationChannel.send(baseNotification, config, "https://apptrail.example.com")

    verify(slackClient, never()).sendMessage(any(), any())
  }
}
