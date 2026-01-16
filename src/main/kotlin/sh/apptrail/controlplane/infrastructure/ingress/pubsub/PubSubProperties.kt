package sh.apptrail.controlplane.infrastructure.ingress.pubsub

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.ingest.pubsub")
data class PubSubProperties(
  val enabled: Boolean = false,
  val subscription: String = "",
)
