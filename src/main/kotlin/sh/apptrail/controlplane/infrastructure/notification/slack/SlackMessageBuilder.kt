package sh.apptrail.controlplane.infrastructure.notification.slack

import sh.apptrail.controlplane.infrastructure.notification.NotificationType
import sh.apptrail.controlplane.infrastructure.notification.SlackChannelConfig
import sh.apptrail.controlplane.infrastructure.notification.model.DeploymentNotification
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object SlackMessageBuilder {

  private val timestampFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
    .withZone(ZoneOffset.UTC)

  fun buildMessage(
    notification: DeploymentNotification,
    frontendBaseUrl: String,
    slackConfig: SlackChannelConfig?,
  ): Map<String, Any> {
    val blocks = mutableListOf<Map<String, Any>>()

    // Header
    blocks.add(buildHeader(notification))

    // Divider
    blocks.add(mapOf("type" to "divider"))

    // Fields section
    blocks.add(buildFieldsSection(notification))

    // Version change
    blocks.add(buildVersionSection(notification))

    // Duration (if available)
    notification.deploymentDurationSeconds?.let { duration ->
      blocks.add(buildDurationSection(duration))
    }

    // Error message (for failures)
    if (notification.type == NotificationType.DEPLOYMENT_FAILED && notification.errorMessage != null) {
      blocks.add(buildErrorSection(notification.errorMessage))
    }

    // Mention on failure
    if (notification.type == NotificationType.DEPLOYMENT_FAILED && slackConfig?.mentionOnFailure != null) {
      blocks.add(buildMentionSection(slackConfig.mentionOnFailure))
    }

    // View Workload button
    blocks.add(buildActionsSection(notification.workloadId, frontendBaseUrl))

    // Timestamp context
    blocks.add(buildTimestampContext(notification))

    return mapOf("blocks" to blocks)
  }

  private fun buildHeader(notification: DeploymentNotification): Map<String, Any> {
    val (emoji, title) = when (notification.type) {
      NotificationType.DEPLOYMENT_STARTED -> ":rocket:" to "Deployment Started"
      NotificationType.DEPLOYMENT_SUCCEEDED -> ":white_check_mark:" to "Deployment Succeeded"
      NotificationType.DEPLOYMENT_FAILED -> ":x:" to "Deployment Failed"
    }

    return mapOf(
      "type" to "header",
      "text" to mapOf(
        "type" to "plain_text",
        "text" to "$emoji $title",
        "emoji" to true
      )
    )
  }

  private fun buildFieldsSection(notification: DeploymentNotification): Map<String, Any> {
    val fields = mutableListOf<Map<String, Any>>()

    fields.add(mapOf(
      "type" to "mrkdwn",
      "text" to "*Workload:*\n${notification.workloadName}"
    ))
    fields.add(mapOf(
      "type" to "mrkdwn",
      "text" to "*Kind:*\n${notification.workloadKind}"
    ))
    fields.add(mapOf(
      "type" to "mrkdwn",
      "text" to "*Environment:*\n${notification.environment}"
    ))
    fields.add(mapOf(
      "type" to "mrkdwn",
      "text" to "*Cluster:*\n${notification.cluster}"
    ))
    fields.add(mapOf(
      "type" to "mrkdwn",
      "text" to "*Namespace:*\n${notification.namespace}"
    ))
    fields.add(mapOf(
      "type" to "mrkdwn",
      "text" to "*Team:*\n${notification.team ?: "N/A"}"
    ))

    return mapOf(
      "type" to "section",
      "fields" to fields
    )
  }

  private fun buildVersionSection(notification: DeploymentNotification): Map<String, Any> {
    val versionText = if (notification.previousVersion != null) {
      "*Version:* `${notification.previousVersion}` -> `${notification.currentVersion}`"
    } else {
      "*Version:* `${notification.currentVersion}`"
    }

    return mapOf(
      "type" to "section",
      "text" to mapOf(
        "type" to "mrkdwn",
        "text" to versionText
      )
    )
  }

  private fun buildDurationSection(durationSeconds: Int): Map<String, Any> {
    val durationText = formatDuration(durationSeconds)

    return mapOf(
      "type" to "section",
      "text" to mapOf(
        "type" to "mrkdwn",
        "text" to ":stopwatch: Deployment took $durationText"
      )
    )
  }

  private fun buildErrorSection(errorMessage: String): Map<String, Any> {
    return mapOf(
      "type" to "section",
      "text" to mapOf(
        "type" to "mrkdwn",
        "text" to ":warning: *Error:* $errorMessage"
      )
    )
  }

  private fun buildMentionSection(mention: String): Map<String, Any> {
    return mapOf(
      "type" to "section",
      "text" to mapOf(
        "type" to "mrkdwn",
        "text" to "cc: $mention"
      )
    )
  }

  private fun buildActionsSection(workloadId: Long, frontendBaseUrl: String): Map<String, Any> {
    val url = "${frontendBaseUrl.trimEnd('/')}/workloads/$workloadId"

    return mapOf(
      "type" to "actions",
      "elements" to listOf(
        mapOf(
          "type" to "button",
          "text" to mapOf(
            "type" to "plain_text",
            "text" to "View Workload",
            "emoji" to true
          ),
          "url" to url
        )
      )
    )
  }

  private fun buildTimestampContext(notification: DeploymentNotification): Map<String, Any> {
    val timestamp = timestampFormatter.format(notification.occurredAt)

    return mapOf(
      "type" to "context",
      "elements" to listOf(
        mapOf(
          "type" to "mrkdwn",
          "text" to "Detected at $timestamp"
        )
      )
    )
  }

  private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60

    return when {
      minutes == 0 -> "${remainingSeconds}s"
      remainingSeconds == 0 -> "${minutes}m"
      else -> "${minutes}m ${remainingSeconds}s"
    }
  }
}
