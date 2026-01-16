package sh.apptrail.controlplane.infrastructure.ingress.pubsub

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.cloud.spring.pubsub.core.PubSubTemplate
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import sh.apptrail.controlplane.application.model.agent.AgentEventPayload

@Configuration
@ConditionalOnProperty(prefix = "app.ingest.pubsub", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(PubSubProperties::class)
class PubSubConfig(
  private val pubSubTemplate: PubSubTemplate,
  private val properties: PubSubProperties,
  private val ingester: GCPPubSubAgentEventIngester,
  private val objectMapper: ObjectMapper,
) {
  private val log = LoggerFactory.getLogger(PubSubConfig::class.java)

  @PostConstruct
  fun subscribeToAgentEvents() {
    if (properties.subscription.isBlank()) {
      log.error("Pub/Sub subscription is not configured. Set app.ingest.pubsub.subscription")
      return
    }

    log.info("Subscribing to Pub/Sub subscription: {}", properties.subscription)

    pubSubTemplate.subscribe(properties.subscription) { message: BasicAcknowledgeablePubsubMessage ->
      val payload = message.pubsubMessage.data.toStringUtf8()
      try {
        log.info("Received Pub/Sub message payload: {}", payload)
        val eventPayload = objectMapper.readValue(payload, AgentEventPayload::class.java)
        ingester.ingest(eventPayload)
        message.ack()
        log.debug("Processed Pub/Sub message: {}", eventPayload.eventId)
      } catch (e: JsonProcessingException) {
        // Ack messages with invalid format to prevent infinite redelivery
        log.error("Invalid message format, acknowledging to discard. Payload: {}. Error: {}", payload, e.message)
        message.ack()
      } catch (e: Exception) {
        // Nack other errors (db, network, etc.) to allow retry
        log.error("Failed to process Pub/Sub message, will retry. Payload: {}. Error: {}", payload, e.message, e)
        message.nack()
      }
    }

    log.info("Pub/Sub subscription active: {}", properties.subscription)
  }

  @PreDestroy
  fun cleanup() {
    log.info("Shutting down Pub/Sub subscription")
  }
}
