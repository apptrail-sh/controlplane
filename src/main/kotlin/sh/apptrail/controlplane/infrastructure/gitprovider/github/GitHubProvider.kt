package sh.apptrail.controlplane.infrastructure.gitprovider.github

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import sh.apptrail.controlplane.infrastructure.gitprovider.GitProvider
import sh.apptrail.controlplane.infrastructure.gitprovider.model.ReleaseAuthor
import sh.apptrail.controlplane.infrastructure.gitprovider.model.ReleaseInfo

/**
 * GitHub implementation of GitProvider.
 * Fetches release information from GitHub Releases API.
 */
@Component
@ConditionalOnProperty(prefix = "app.gitprovider.github", name = ["enabled"], havingValue = "true")
class GitHubProvider(
  private val authService: GitHubAuthService,
  private val apiClient: GitHubApiClient,
) : GitProvider {

  private val log = LoggerFactory.getLogger(GitHubProvider::class.java)

  override val providerId = "github"

  override fun supports(repositoryUrl: String): Boolean {
    return repositoryUrl.contains("github.com")
  }

  override fun fetchRelease(repositoryUrl: String, version: String): ReleaseInfo? {
    val (owner, repo) = parseRepositoryUrl(repositoryUrl)
    log.debug("Fetching release for {}/{} version {}", owner, repo, version)

    val token = authService.getInstallationToken(owner)

    // Try different tag formats: exact, with 'v' prefix, without 'v' prefix
    val tagCandidates = listOf(
      version,
      "v$version",
      version.removePrefix("v")
    ).distinct()

    for (tag in tagCandidates) {
      val release = apiClient.getReleaseByTag(owner, repo, tag, token)
      if (release != null) {
        log.info("Found release for {}/{} with tag {}", owner, repo, tag)
        return mapToReleaseInfo(release)
      }
    }

    log.debug("No release found for {}/{} version {}", owner, repo, version)
    return null
  }

  private fun parseRepositoryUrl(repositoryUrl: String): Pair<String, String> {
    // Handle various GitHub URL formats:
    // https://github.com/owner/repo
    // https://github.com/owner/repo.git
    // git@github.com:owner/repo.git
    // github.com/owner/repo

    val cleanUrl = repositoryUrl
      .removePrefix("https://")
      .removePrefix("http://")
      .removePrefix("git@")
      .removeSuffix(".git")
      .replace("github.com:", "github.com/")

    val parts = cleanUrl.removePrefix("github.com/").split("/")
    if (parts.size < 2) {
      throw IllegalArgumentException("Invalid GitHub repository URL: $repositoryUrl")
    }

    return Pair(parts[0], parts[1])
  }

  private fun mapToReleaseInfo(release: GitHubRelease): ReleaseInfo {
    val authors = if (release.author != null) {
      listOf(
        ReleaseAuthor(
          login = release.author.login,
          avatarUrl = release.author.avatarUrl,
        )
      )
    } else {
      emptyList()
    }

    return ReleaseInfo(
      provider = providerId,
      tagName = release.tagName,
      name = release.name,
      body = release.body,
      publishedAt = release.publishedAt,
      htmlUrl = release.htmlUrl,
      authors = authors,
      isDraft = release.draft,
      isPrerelease = release.prerelease,
    )
  }
}
