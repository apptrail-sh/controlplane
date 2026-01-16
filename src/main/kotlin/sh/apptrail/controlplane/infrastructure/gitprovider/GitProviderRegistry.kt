package sh.apptrail.controlplane.infrastructure.gitprovider

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Registry for Git providers. Selects the appropriate provider based on repository URL.
 */
@Component
class GitProviderRegistry(
  private val providers: List<GitProvider>,
) {
  private val log = LoggerFactory.getLogger(GitProviderRegistry::class.java)

  /**
   * Finds a provider that can handle the given repository URL
   * @return The matching provider, or null if no provider supports the URL
   */
  fun findProvider(repositoryUrl: String): GitProvider? {
    val provider = providers.find { it.supports(repositoryUrl) }
    if (provider == null) {
      log.debug("No Git provider found for repository URL: {}", repositoryUrl)
    }
    return provider
  }
}
