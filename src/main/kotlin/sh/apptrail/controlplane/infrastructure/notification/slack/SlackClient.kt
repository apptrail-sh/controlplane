package sh.apptrail.controlplane.infrastructure.notification.slack

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class SlackClient {

  private val log = LoggerFactory.getLogger(SlackClient::class.java)
  private val restClient = RestClient.create()

  fun sendMessage(webhookUrl: String, payload: Map<String, Any>) {
    try {
      restClient.post()
        .uri(webhookUrl)
        .contentType(MediaType.APPLICATION_JSON)
        .body(payload)
        .retrieve()
        .toBodilessEntity()
    } catch (e: Exception) {
      log.error("Failed to send Slack message to webhook", e)
      throw e
    }
  }
}
