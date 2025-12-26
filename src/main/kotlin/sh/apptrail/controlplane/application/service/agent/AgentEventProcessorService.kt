package sh.apptrail.controlplane.application.service.agent

import org.springframework.stereotype.Service
import sh.apptrail.controlplane.application.model.agent.AgentEvent

@Service
class AgentEventProcessorService {
  fun processEvent(eventPayload: AgentEvent) {
    // TODO: persist event, update metrics, and dispatch notifications.
  }
}
