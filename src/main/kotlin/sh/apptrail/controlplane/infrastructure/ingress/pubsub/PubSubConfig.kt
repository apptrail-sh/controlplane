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
import sh.apptrail.controlplane.application.model.heartbeat.ClusterHeartbeatPayload
import sh.apptrail.controlplane.application.model.infrastructure.ResourceEventPayload
import sh.apptrail.controlplane.application.service.cleanup.ClusterHeartbeatService
import sh.apptrail.controlplane.application.service.infrastructure.ResourceEventProcessorService
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

@Configuration
@ConditionalOnProperty(prefix = "app.ingest.pubsub", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(PubSubProperties::class)
class PubSubConfig(
  private val properties: PubSubProperties,
  private val ingester: GCPPubSubAgentEventIngester,
  private val resourceEventProcessor: ResourceEventProcessorService,
  private val clusterHeartbeatService: ClusterHeartbeatService,
  private val jsonMapper: JsonMapper,
) {
  private val log = LoggerFactory.getLogger(PubSubConfig::class.java)
  private var subscriber: Subscriber? = null

  companion object {
    private const val MESSAGE_TYPE_ATTRIBUTE = "message_type"
    private const val RESOURCE_EVENT_TYPE = "resource_event"
    private const val HEARTBEAT_TYPE = "heartbeat"
  }

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
      val messageType = message.attributesMap[MESSAGE_TYPE_ATTRIBUTE]

      try {
        if (messageType == RESOURCE_EVENT_TYPE) {
          processResourceEvent(payload)
        } else if (messageType == HEARTBEAT_TYPE) {
          processHeartbeatEvent(payload)
        } else {
          processWorkloadEvent(payload)
        }
        consumer.ack()
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

  private fun processWorkloadEvent(payload: String) {
    log.debug("Processing workload event from Pub/Sub")
    val eventPayload = jsonMapper.readValue(payload, AgentEventPayload::class.java)
    ingester.ingest(eventPayload)
    log.debug("Processed workload event: {}", eventPayload.eventId)
  }

  private fun processResourceEvent(payload: String) {
    log.debug("Processing resource event from Pub/Sub")
    val eventPayload = jsonMapper.readValue(payload, ResourceEventPayload::class.java)
    resourceEventProcessor.processEvent(eventPayload)
    log.debug("Processed resource event: {} {}/{}",
      eventPayload.resourceType, eventPayload.resource.namespace ?: "", eventPayload.resource.name)
  }

  private fun processHeartbeatEvent(payload: String) {
    log.debug("Processing heartbeat event from Pub/Sub")
    val heartbeatPayload = jsonMapper.readValue(payload, ClusterHeartbeatPayload::class.java)
    clusterHeartbeatService.processHeartbeat(heartbeatPayload)
    log.debug("Processed heartbeat event: {} from cluster {}",
      heartbeatPayload.eventId, heartbeatPayload.source.clusterId)
  }

  @PreDestroy
  fun cleanup() {
    log.info("Shutting down Pub/Sub subscription")
    subscriber?.stopAsync()
  }
}
