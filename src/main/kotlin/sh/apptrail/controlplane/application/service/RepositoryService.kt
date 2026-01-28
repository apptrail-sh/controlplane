package sh.apptrail.controlplane.application.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.gitprovider.GitProviderRegistry
import sh.apptrail.controlplane.infrastructure.persistence.entity.RepositoryEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.RepositoryRepository

@Service
class RepositoryService(
  private val repositoryRepository: RepositoryRepository,
  private val providerRegistry: GitProviderRegistry,
) {
  private val log = LoggerFactory.getLogger(RepositoryService::class.java)

  /**
   * Finds or creates a repository for the given URL.
   * Normalizes the URL and extracts provider/owner/name.
   */
  fun findOrCreate(rawUrl: String): RepositoryEntity {
    val normalized = normalizeUrl(rawUrl)
    return repositoryRepository.findByUrl(normalized)
      ?: createRepository(normalized, rawUrl)
  }

  /**
   * Finds a repository by its normalized URL.
   */
  fun findByUrl(url: String): RepositoryEntity? {
    val normalized = normalizeUrl(url)
    return repositoryRepository.findByUrl(normalized)
  }

  private fun normalizeUrl(url: String): String {
    return url.lowercase()
      .removeSuffix(".git")
      .removeSuffix("/")
  }

  private fun createRepository(normalizedUrl: String, rawUrl: String): RepositoryEntity {
    val provider = providerRegistry.findProvider(rawUrl)
    val (owner, name) = parseOwnerAndName(normalizedUrl)

    log.info("Creating repository record for {} (provider: {})", normalizedUrl, provider?.providerId ?: "unknown")

    return repositoryRepository.save(
      RepositoryEntity(
        url = normalizedUrl,
        provider = provider?.providerId ?: "unknown",
        owner = owner,
        name = name,
      )
    )
  }

  private fun parseOwnerAndName(url: String): Pair<String?, String?> {
    val regex = Regex("""(?:github|gitlab)\.com/([^/]+)/([^/]+)""")
    val match = regex.find(url)
    return if (match != null) {
      Pair(match.groupValues[1], match.groupValues[2].removeSuffix(".git"))
    } else {
      Pair(null, null)
    }
  }
}
