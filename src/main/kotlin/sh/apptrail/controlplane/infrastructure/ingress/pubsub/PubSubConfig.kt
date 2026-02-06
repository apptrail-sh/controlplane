package sh.apptrail.controlplane.infrastructure.ingress.pubsub

import com.google.api.gax.batching.FlowControlSettings
import com.google.api.gax.core.ExecutorProvider
import com.google.api.gax.core.InstantiatingExecutorProvider
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
@ConditionalOnProperty(prefix = "apptrail.ingest.pubsub", name = ["enabled"], havingValue = "true")
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

    // Concurrency settings for message processing
    private const val PARALLEL_PULL_COUNT = 4
    private const val EXECUTOR_THREAD_COUNT = 8
    private const val MAX_OUTSTANDING_ELEMENT_COUNT = 100L
    private const val MAX_OUTSTANDING_REQUEST_BYTES = 10L * 1024L * 1024L // 10MB
  }

  private var executorProvider: ExecutorProvider? = null

  @PostConstruct
  fun subscribeToAgentEvents() {
    if (properties.subscription.isBlank()) {
      log.error("Pub/Sub subscription is not configured. Set apptrail.ingest.pubsub.subscription")
      return
    }

    log.info("Subscribing to Pub/Sub subscription: {}", properties.subscription)

    val subscriptionName = ProjectSubscriptionName.parse(properties.subscription)

    val receiver = MessageReceiver { message: PubsubMessage, consumer: AckReplyConsumer ->
      val payload = message.data.toStringUtf8()
      val messageType = message.attributesMap[MESSAGE_TYPE_ATTRIBUTE]

      try {
        when (messageType) {
          RESOURCE_EVENT_TYPE -> processResourceEvent(payload)
          HEARTBEAT_TYPE -> processHeartbeatEvent(payload)
          else -> processWorkloadEvent(payload)
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

    executorProvider = InstantiatingExecutorProvider.newBuilder()
      .setExecutorThreadCount(EXECUTOR_THREAD_COUNT)
      .build()

    val flowControlSettings = FlowControlSettings.newBuilder()
      .setMaxOutstandingElementCount(MAX_OUTSTANDING_ELEMENT_COUNT)
      .setMaxOutstandingRequestBytes(MAX_OUTSTANDING_REQUEST_BYTES)
      .build()

    subscriber = Subscriber.newBuilder(subscriptionName, receiver)
      .setParallelPullCount(PARALLEL_PULL_COUNT)
      .setFlowControlSettings(flowControlSettings)
      .setExecutorProvider(executorProvider)
      .build()

    subscriber?.startAsync()?.awaitRunning()

    log.info(
      "Pub/Sub subscription active: {} (parallelPullCount={}, executorThreads={}, maxOutstanding={})",
      properties.subscription, PARALLEL_PULL_COUNT, EXECUTOR_THREAD_COUNT, MAX_OUTSTANDING_ELEMENT_COUNT
    )
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
    log.debug(
      "Processed resource event: {} {}/{}",
      eventPayload.resourceType, eventPayload.resource.namespace ?: "", eventPayload.resource.name
    )
  }

  private fun processHeartbeatEvent(payload: String) {
    log.debug("Processing heartbeat event from Pub/Sub")
    val heartbeatPayload = jsonMapper.readValue(payload, ClusterHeartbeatPayload::class.java)
    clusterHeartbeatService.processHeartbeat(heartbeatPayload)
    log.debug(
      "Processed heartbeat event: {} from cluster {}",
      heartbeatPayload.eventId, heartbeatPayload.source.clusterId
    )
  }

  @PreDestroy
  fun cleanup() {
    log.info("Shutting down Pub/Sub subscription")
    subscriber?.stopAsync()
  }
}
