package sh.apptrail.controlplane.infrastructure.config

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "apptrail.quick-links")
data class QuickLinksProperties(
  val links: List<QuickLinkConfig> = emptyList(),
)

enum class QuickLinkType {
  @JsonProperty("url")
  URL,
  @JsonProperty("command")
  COMMAND
}

data class QuickLinkConfig(
  val name: String,
  val description: String? = null,
  val urlTemplate: String,
  val linkType: QuickLinkType = QuickLinkType.URL,
  val icon: String? = null,
)
