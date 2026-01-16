package sh.apptrail.controlplane.infrastructure.gitprovider

import sh.apptrail.controlplane.infrastructure.gitprovider.model.ReleaseInfo

/**
 * Interface for Git provider integrations (GitHub, GitLab, Bitbucket, etc.)
 */
interface GitProvider {

  /**
   * Provider identifier for logging and metrics
   */
  val providerId: String

  /**
   * Returns true if this provider can handle the given repository URL
   */
  fun supports(repositoryUrl: String): Boolean

  /**
   * Fetches release information for a given version tag
   * @param repositoryUrl The full repository URL (e.g., https://github.com/org/repo)
   * @param version The version to match (tries with and without 'v' prefix)
   * @return ReleaseInfo if found, null otherwise
   */
  fun fetchRelease(repositoryUrl: String, version: String): ReleaseInfo?
}
