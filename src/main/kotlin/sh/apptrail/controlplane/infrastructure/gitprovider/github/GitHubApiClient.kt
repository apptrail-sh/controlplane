package sh.apptrail.controlplane.infrastructure.gitprovider.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.time.Instant

/**
 * REST client for GitHub API.
 */
@Component
@ConditionalOnProperty(prefix = "apptrail.gitprovider.github", name = ["enabled"], havingValue = "true")
class GitHubApiClient(
  private val properties: GitHubProperties,
) {
  private val log = LoggerFactory.getLogger(GitHubApiClient::class.java)
  private val restTemplate = RestTemplate()

  /**
   * Gets a release by tag name.
   * @return GitHubRelease if found, null if not found (404)
   */
  fun getReleaseByTag(owner: String, repo: String, tag: String, token: String): GitHubRelease? {
    val headers = createHeaders(token)
    val url = "${properties.apiBaseUrl}/repos/$owner/$repo/releases/tags/$tag"

    return try {
      val response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        HttpEntity<Any>(headers),
        GitHubRelease::class.java
      )
      response.body
    } catch (e: HttpClientErrorException.NotFound) {
      log.debug("Release not found for tag '{}' in {}/{}", tag, owner, repo)
      null
    } catch (e: Exception) {
      log.error("Error fetching release for tag '{}' in {}/{}: {}", tag, owner, repo, e.message)
      throw e
    }
  }

  private fun createHeaders(token: String): HttpHeaders {
    return HttpHeaders().apply {
      set("Authorization", "Bearer $token")
      set("Accept", "application/vnd.github+json")
      set("X-GitHub-Api-Version", "2022-11-28")
    }
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubRelease(
  val id: Long,

  @JsonProperty("tag_name")
  val tagName: String,

  val name: String?,

  val body: String?,

  @JsonProperty("published_at")
  val publishedAt: Instant?,

  @JsonProperty("html_url")
  val htmlUrl: String?,

  val draft: Boolean = false,

  val prerelease: Boolean = false,

  val author: GitHubUser?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubUser(
  val login: String,

  @JsonProperty("avatar_url")
  val avatarUrl: String?,
)
