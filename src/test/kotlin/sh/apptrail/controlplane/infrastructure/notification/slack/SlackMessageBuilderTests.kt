package sh.apptrail.controlplane.infrastructure.notification.slack

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import sh.apptrail.controlplane.infrastructure.notification.NotificationType
import sh.apptrail.controlplane.infrastructure.notification.SlackChannelConfig
import sh.apptrail.controlplane.infrastructure.notification.model.DeploymentNotification
import java.time.Instant

class SlackMessageBuilderTests {

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
    occurredAt = Instant.parse("2026-01-23T10:15:30Z"),
    deploymentDurationSeconds = 154,
    errorMessage = null,
  )

  @Test
  fun `buildMessage includes all required blocks`() {
    val message = SlackMessageBuilder.buildMessage(
      notification = baseNotification,
      frontendBaseUrl = "https://apptrail.example.com",
      slackConfig = null,
    )

    @Suppress("UNCHECKED_CAST")
    val blocks = message["blocks"] as List<Map<String, Any>>
    assertNotNull(blocks)
    assertTrue(blocks.size >= 5, "Should have at least 5 blocks")

    val blockTypes = blocks.map { it["type"] }
    assertTrue(blockTypes.contains("header"))
    assertTrue(blockTypes.contains("divider"))
    assertTrue(blockTypes.contains("section"))
    assertTrue(blockTypes.contains("actions"))
    assertTrue(blockTypes.contains("context"))
  }

  @Test
  fun `buildMessage includes correct header for success`() {
    val message = SlackMessageBuilder.buildMessage(
      notification = baseNotification,
      frontendBaseUrl = "https://apptrail.example.com",
      slackConfig = null,
    )

    @Suppress("UNCHECKED_CAST")
    val blocks = message["blocks"] as List<Map<String, Any>>
    @Suppress("UNCHECKED_CAST")
    val header = blocks.first { it["type"] == "header" }
    @Suppress("UNCHECKED_CAST")
    val text = header["text"] as Map<String, Any>

    assertEquals(":white_check_mark: Deployment Succeeded", text["text"])
  }

  @Test
  fun `buildMessage includes correct header for failure`() {
    val failedNotification = baseNotification.copy(type = NotificationType.DEPLOYMENT_FAILED)

    val message = SlackMessageBuilder.buildMessage(
      notification = failedNotification,
      frontendBaseUrl = "https://apptrail.example.com",
      slackConfig = null,
    )

    @Suppress("UNCHECKED_CAST")
    val blocks = message["blocks"] as List<Map<String, Any>>
    @Suppress("UNCHECKED_CAST")
    val header = blocks.first { it["type"] == "header" }
    @Suppress("UNCHECKED_CAST")
    val text = header["text"] as Map<String, Any>

    assertEquals(":x: Deployment Failed", text["text"])
  }

  @Test
  fun `buildMessage includes correct header for started`() {
    val startedNotification = baseNotification.copy(type = NotificationType.DEPLOYMENT_STARTED)

    val message = SlackMessageBuilder.buildMessage(
      notification = startedNotification,
      frontendBaseUrl = "https://apptrail.example.com",
      slackConfig = null,
    )

    @Suppress("UNCHECKED_CAST")
    val blocks = message["blocks"] as List<Map<String, Any>>
    @Suppress("UNCHECKED_CAST")
    val header = blocks.first { it["type"] == "header" }
    @Suppress("UNCHECKED_CAST")
    val text = header["text"] as Map<String, Any>

    assertEquals(":rocket: Deployment Started", text["text"])
  }

  @Test
  fun `buildMessage includes View Workload button with correct URL`() {
    val message = SlackMessageBuilder.buildMessage(
      notification = baseNotification,
      frontendBaseUrl = "https://apptrail.example.com",
      slackConfig = null,
    )

    @Suppress("UNCHECKED_CAST")
    val blocks = message["blocks"] as List<Map<String, Any>>
    @Suppress("UNCHECKED_CAST")
    val actions = blocks.first { it["type"] == "actions" }
    @Suppress("UNCHECKED_CAST")
    val elements = actions["elements"] as List<Map<String, Any>>
    val button = elements.first()

    assertEquals("https://apptrail.example.com/workloads/123", button["url"])
  }

  @Test
  fun `buildMessage includes version change with arrow`() {
    val message = SlackMessageBuilder.buildMessage(
      notification = baseNotification,
      frontendBaseUrl = "https://apptrail.example.com",
      slackConfig = null,
    )

    @Suppress("UNCHECKED_CAST")
    val blocks = message["blocks"] as List<Map<String, Any>>

    val versionSection = blocks.filter { it["type"] == "section" }
      .map { (it["text"] as? Map<*, *>)?.get("text") as? String }
      .filterNotNull()
      .firstOrNull { it.contains("Version") }

    assertNotNull(versionSection)
    assertTrue(versionSection!!.contains("`v1.2.3` -> `v1.2.4`"))
  }

  @Test
  fun `buildMessage includes duration when provided`() {
    val message = SlackMessageBuilder.buildMessage(
      notification = baseNotification,
      frontendBaseUrl = "https://apptrail.example.com",
      slackConfig = null,
    )

    @Suppress("UNCHECKED_CAST")
    val blocks = message["blocks"] as List<Map<String, Any>>

    val durationSection = blocks.filter { it["type"] == "section" }
      .map { (it["text"] as? Map<*, *>)?.get("text") as? String }
      .filterNotNull()
      .firstOrNull { it.contains("Deployment took") }

    assertNotNull(durationSection)
    assertTrue(durationSection!!.contains("2m 34s"))
  }

  @Test
  fun `buildMessage includes error message for failures`() {
    val failedNotification = baseNotification.copy(
      type = NotificationType.DEPLOYMENT_FAILED,
      errorMessage = "Readiness probe failed"
    )

    val message = SlackMessageBuilder.buildMessage(
      notification = failedNotification,
      frontendBaseUrl = "https://apptrail.example.com",
      slackConfig = null,
    )

    @Suppress("UNCHECKED_CAST")
    val blocks = message["blocks"] as List<Map<String, Any>>

    val errorSection = blocks.filter { it["type"] == "section" }
      .map { (it["text"] as? Map<*, *>)?.get("text") as? String }
      .filterNotNull()
      .firstOrNull { it.contains("Error") }

    assertNotNull(errorSection)
    assertTrue(errorSection!!.contains("Readiness probe failed"))
  }

  @Test
  fun `buildMessage includes mention on failure when configured`() {
    val failedNotification = baseNotification.copy(type = NotificationType.DEPLOYMENT_FAILED)
    val slackConfig = SlackChannelConfig(
      webhookUrl = "https://hooks.slack.com/services/xxx",
      channel = "#alerts",
      mentionOnFailure = "@oncall"
    )

    val message = SlackMessageBuilder.buildMessage(
      notification = failedNotification,
      frontendBaseUrl = "https://apptrail.example.com",
      slackConfig = slackConfig,
    )

    @Suppress("UNCHECKED_CAST")
    val blocks = message["blocks"] as List<Map<String, Any>>

    val mentionSection = blocks.filter { it["type"] == "section" }
      .map { (it["text"] as? Map<*, *>)?.get("text") as? String }
      .filterNotNull()
      .firstOrNull { it.contains("cc:") }

    assertNotNull(mentionSection)
    assertTrue(mentionSection!!.contains("@oncall"))
  }

  @Test
  fun `buildMessage does not include mention for success even when configured`() {
    val slackConfig = SlackChannelConfig(
      webhookUrl = "https://hooks.slack.com/services/xxx",
      channel = "#alerts",
      mentionOnFailure = "@oncall"
    )

    val message = SlackMessageBuilder.buildMessage(
      notification = baseNotification,
      frontendBaseUrl = "https://apptrail.example.com",
      slackConfig = slackConfig,
    )

    @Suppress("UNCHECKED_CAST")
    val blocks = message["blocks"] as List<Map<String, Any>>

    val mentionSection = blocks.filter { it["type"] == "section" }
      .map { (it["text"] as? Map<*, *>)?.get("text") as? String }
      .filterNotNull()
      .firstOrNull { it.contains("cc:") }

    assertNull(mentionSection)
  }

  @Test
  fun `buildMessage handles notification without previous version`() {
    val noPreviousVersion = baseNotification.copy(previousVersion = null)

    val message = SlackMessageBuilder.buildMessage(
      notification = noPreviousVersion,
      frontendBaseUrl = "https://apptrail.example.com",
      slackConfig = null,
    )

    @Suppress("UNCHECKED_CAST")
    val blocks = message["blocks"] as List<Map<String, Any>>

    val versionSection = blocks.filter { it["type"] == "section" }
      .map { (it["text"] as? Map<*, *>)?.get("text") as? String }
      .filterNotNull()
      .firstOrNull { it.contains("Version") }

    assertNotNull(versionSection)
    assertTrue(versionSection!!.contains("`v1.2.4`"))
    assertFalse(versionSection.contains("->"))
  }

  @Test
  fun `buildMessage trims trailing slash from frontend base URL`() {
    val message = SlackMessageBuilder.buildMessage(
      notification = baseNotification,
      frontendBaseUrl = "https://apptrail.example.com/",
      slackConfig = null,
    )

    @Suppress("UNCHECKED_CAST")
    val blocks = message["blocks"] as List<Map<String, Any>>
    @Suppress("UNCHECKED_CAST")
    val actions = blocks.first { it["type"] == "actions" }
    @Suppress("UNCHECKED_CAST")
    val elements = actions["elements"] as List<Map<String, Any>>
    val button = elements.first()

    assertEquals("https://apptrail.example.com/workloads/123", button["url"])
    assertFalse((button["url"] as String).contains("//workloads"))
  }
}
