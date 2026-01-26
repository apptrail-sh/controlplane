package sh.apptrail.controlplane.infrastructure.ingress.pubsub

import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.cloud.pubsub.v1.Subscriber
import com.google.pubsub.v1.ProjectSubscriptionName
import com.google.pubsub.v1.PubsubMessage
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import sh.apptrail.controlplane.application.model.agent.AgentEventPayload
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

@Configuration
@ConditionalOnProperty(prefix = "app.ingest.pubsub", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(PubSubProperties::class)
class PubSubConfig(
  private val properties: PubSubProperties,
  private val ingester: GCPPubSubAgentEventIngester,
  private val jsonMapper: JsonMapper,
) {
  private val log = LoggerFactory.getLogger(PubSubConfig::class.java)
  private var subscriber: Subscriber? = null

  @PostConstruct
  fun subscribeToAgentEvents() {
    if (properties.subscription.isBlank()) {
      log.error("Pub/Sub subscription is not configured. Set app.ingest.pubsub.subscription")
      return
    }

    log.info("Subscribing to Pub/Sub subscription: {}", properties.subscription)

    val subscriptionName = ProjectSubscriptionName.parse(properties.subscription)

    val receiver = MessageReceiver { message: PubsubMessage, consumer: AckReplyConsumer ->
      val payload = message.data.toStringUtf8()
      try {
        log.info("Received Pub/Sub message payload: {}", payload)
        val eventPayload = jsonMapper.readValue(payload, AgentEventPayload::class.java)
        ingester.ingest(eventPayload)
        consumer.ack()
        log.debug("Processed Pub/Sub message: {}", eventPayload.eventId)
      } catch (e: JacksonException) {
        // Ack messages with invalid format to prevent infinite redelivery
        log.error("Invalid message format, acknowledging to discard. Payload: {}. Error: {}", payload, e.message)
        consumer.ack()
      } catch (e: Exception) {
        // Nack other errors (db, network, etc.) to allow retry
        log.error("Failed to process Pub/Sub message, will retry. Payload: {}. Error: {}", payload, e.message, e)
        consumer.nack()
      }
    }

    subscriber = Subscriber.newBuilder(subscriptionName, receiver).build()
    subscriber?.startAsync()?.awaitRunning()

    log.info("Pub/Sub subscription active: {}", properties.subscription)
  }

  @PreDestroy
  fun cleanup() {
    log.info("Shutting down Pub/Sub subscription")
    subscriber?.stopAsync()
  }
}
