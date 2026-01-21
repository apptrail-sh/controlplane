package sh.apptrail.controlplane.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "apptrail.quick-links")
data class QuickLinksProperties(
  val links: List<QuickLinkConfig> = emptyList(),
)

data class QuickLinkConfig(
  val name: String,
  val description: String? = null,
  val urlTemplate: String,
  val linkType: String = "url",
  val icon: String? = null,
)
