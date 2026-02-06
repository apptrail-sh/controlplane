package sh.apptrail.controlplane.infrastructure.gitprovider.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.gitprovider.model.ReleaseAuthor
import sh.apptrail.controlplane.infrastructure.gitprovider.model.ReleaseInfo
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
@ConditionalOnProperty(prefix = "apptrail.gitprovider.github", name = ["enabled"], havingValue = "true")
class GitHubWebhookService(
  private val properties: GitHubProperties,
  private val jsonMapper: JsonMapper,
) {
  private val log = LoggerFactory.getLogger(GitHubWebhookService::class.java)

  companion object {
    private const val HMAC_SHA256 = "HmacSHA256"
    private const val SIGNATURE_PREFIX = "sha256="
  }

  /**
   * Validates the GitHub webhook signature.
   * @param payload The raw request body
   * @param signature The X-Hub-Signature-256 header value
   * @return true if the signature is valid
   */
  fun validateSignature(payload: ByteArray, signature: String?): Boolean {
    val secret = properties.webhookSecret
    if (secret.isNullOrBlank()) {
      log.warn("Webhook secret not configured, skipping signature validation")
      return true
    }

    if (signature.isNullOrBlank()) {
      log.warn("Missing webhook signature header")
      return false
    }

    if (!signature.startsWith(SIGNATURE_PREFIX)) {
      log.warn("Invalid signature format: missing sha256= prefix")
      return false
    }

    val expectedSignature = signature.removePrefix(SIGNATURE_PREFIX)
    val computedSignature = computeHmacSha256(payload, secret)

    val isValid = constantTimeEquals(expectedSignature, computedSignature)
    if (!isValid) {
      log.warn("Webhook signature validation failed")
    }
    return isValid
  }

  /**
   * Parses a GitHub release webhook payload.
   * @return Pair of repository URL and ReleaseInfo, or null if parsing fails or event is not relevant
   */
  fun parseReleasePayload(payload: String): GitHubReleaseWebhookPayload? {
    return try {
      val webhookPayload = jsonMapper.readValue(payload, GitHubWebhookPayload::class.java)

      // Only process 'published' action for releases
      if (webhookPayload.action != "published" && webhookPayload.action != "edited") {
        log.debug("Ignoring release webhook with action: {}", webhookPayload.action)
        return null
      }

      val release = webhookPayload.release ?: return null
      val repository = webhookPayload.repository ?: return null

      val releaseInfo = ReleaseInfo(
        provider = "github",
        tagName = release.tagName,
        name = release.name,
        body = release.body,
        publishedAt = release.publishedAt,
        htmlUrl = release.htmlUrl,
        authors = if (release.author != null) {
          listOf(ReleaseAuthor(login = release.author.login, avatarUrl = release.author.avatarUrl))
        } else {
          emptyList()
        },
        isDraft = release.draft,
        isPrerelease = release.prerelease,
      )

      GitHubReleaseWebhookPayload(
        repositoryUrl = repository.htmlUrl,
        releaseInfo = releaseInfo,
      )
    } catch (e: Exception) {
      log.error("Failed to parse release webhook payload: {}", e.message)
      null
    }
  }

  private fun computeHmacSha256(payload: ByteArray, secret: String): String {
    val mac = Mac.getInstance(HMAC_SHA256)
    val secretKey = SecretKeySpec(secret.toByteArray(), HMAC_SHA256)
    mac.init(secretKey)
    val hash = mac.doFinal(payload)
    return hash.joinToString("") { "%02x".format(it) }
  }

  private fun constantTimeEquals(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var result = 0
    for (i in a.indices) {
      result = result or (a[i].code xor b[i].code)
    }
    return result == 0
  }
}

data class GitHubReleaseWebhookPayload(
  val repositoryUrl: String,
  val releaseInfo: ReleaseInfo,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubWebhookPayload(
  val action: String?,
  val release: GitHubWebhookRelease?,
  val repository: GitHubWebhookRepository?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubWebhookRelease(
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
  val author: GitHubWebhookUser?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubWebhookRepository(
  @JsonProperty("html_url")
  val htmlUrl: String,
  @JsonProperty("full_name")
  val fullName: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubWebhookUser(
  val login: String,
  @JsonProperty("avatar_url")
  val avatarUrl: String?,
)
