package sh.apptrail.controlplane.application.service.infrastructure

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.application.model.infrastructure.ResourceEventPayload
import sh.apptrail.controlplane.application.model.infrastructure.ResourceType

@Service
class ResourceEventProcessorService(
  private val nodeService: NodeService,
  private val podService: PodService
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun processEvent(event: ResourceEventPayload) {
    log.debug("Processing resource event: type={}, kind={}, resource={}/{}",
      event.resourceType, event.eventKind,
      event.resource.namespace ?: "", event.resource.name)

    when (event.resourceType) {
      ResourceType.NODE -> nodeService.processNodeEvent(event)
      ResourceType.POD -> podService.processPodEvent(event)
      ResourceType.WORKLOAD -> {
        log.debug("Workload events handled by AgentEventProcessorService, skipping")
      }
      ResourceType.SERVICE -> {
        log.debug("Service tracking not yet implemented")
      }
    }
  }

  fun processBatch(events: List<ResourceEventPayload>) {
    log.info("Processing resource event batch with {} events", events.size)
    events.forEach { event ->
      try {
        processEvent(event)
      } catch (e: Exception) {
        log.error("Failed to process resource event: {} {} {}/{}",
          event.resourceType, event.eventKind,
          event.resource.namespace ?: "", event.resource.name, e)
      }
    }
  }
}
