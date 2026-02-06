package sh.apptrail.controlplane.infrastructure.gitprovider.github

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "apptrail.gitprovider.github")
data class GitHubProperties(
  val enabled: Boolean = false,
  val appId: String = "",
  val privateKeyPath: String? = null,
  val privateKeyBase64: String? = null,
  val apiBaseUrl: String = "https://api.github.com",
  val webhookSecret: String? = null,
)
