package sh.apptrail.controlplane.infrastructure.gitprovider.github

import io.jsonwebtoken.Jwts
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Paths
import java.security.PrivateKey
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles GitHub App authentication: JWT generation and installation token management.
 */
@Service
@ConditionalOnProperty(prefix = "apptrail.gitprovider.github", name = ["enabled"], havingValue = "true")
class GitHubAuthService(
  private val properties: GitHubProperties,
) {
  private val log = LoggerFactory.getLogger(GitHubAuthService::class.java)
  private val restTemplate = RestTemplate()
  private val tokenCache = ConcurrentHashMap<String, CachedToken>()
  private val privateKey: PrivateKey by lazy { loadPrivateKey() }

  /**
   * Gets an installation access token for the given owner (user or org).
   * Tokens are cached for ~55 minutes (GitHub tokens expire after 1 hour).
   */
  fun getInstallationToken(owner: String): String {
    val cached = tokenCache[owner]
    if (cached != null && !cached.isExpired()) {
      log.debug("Using cached installation token for owner: {}", owner)
      return cached.token
    }

    log.info("Fetching new installation token for owner: {}", owner)
    val jwt = generateJwt()
    val installationId = findInstallationId(jwt, owner)
    val token = exchangeForAccessToken(jwt, installationId)

    // Cache for 55 minutes (5 minute buffer before 1-hour expiry)
    tokenCache[owner] = CachedToken(token, Instant.now().plusSeconds(3300))
    return token
  }

  private fun generateJwt(): String {
    val now = Instant.now()
    return Jwts.builder()
      .issuer(properties.appId)
      .issuedAt(Date.from(now.minusSeconds(60))) // 60 seconds in past for clock drift
      .expiration(Date.from(now.plusSeconds(540))) // 9 minutes (max is 10)
      .signWith(privateKey)
      .compact()
  }

  private fun findInstallationId(jwt: String, owner: String): Long {
    val headers = HttpHeaders().apply {
      set("Authorization", "Bearer $jwt")
      set("Accept", "application/vnd.github+json")
      set("X-GitHub-Api-Version", "2022-11-28")
    }

    // Try to get installation for the specific owner
    val url = "${properties.apiBaseUrl}/users/$owner/installation"
    return try {
      val response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        HttpEntity<Any>(headers),
        InstallationResponse::class.java
      )
      response.body?.id ?: throw IllegalStateException("No installation ID in response")
    } catch (e: Exception) {
      // Try orgs endpoint if user endpoint fails
      try {
        val orgUrl = "${properties.apiBaseUrl}/orgs/$owner/installation"
        val response = restTemplate.exchange(
          orgUrl,
          HttpMethod.GET,
          HttpEntity<Any>(headers),
          InstallationResponse::class.java
        )
        response.body?.id ?: throw IllegalStateException("No installation ID in response")
      } catch (orgE: Exception) {
        log.error("Failed to find installation for owner '{}'. Ensure the GitHub App is installed.", owner)
        throw IllegalStateException("GitHub App not installed for owner: $owner", orgE)
      }
    }
  }

  private fun exchangeForAccessToken(jwt: String, installationId: Long): String {
    val headers = HttpHeaders().apply {
      set("Authorization", "Bearer $jwt")
      set("Accept", "application/vnd.github+json")
      set("X-GitHub-Api-Version", "2022-11-28")
    }

    val url = "${properties.apiBaseUrl}/app/installations/$installationId/access_tokens"
    val response = restTemplate.exchange(
      url,
      HttpMethod.POST,
      HttpEntity<Any>(headers),
      AccessTokenResponse::class.java
    )

    return response.body?.token
      ?: throw IllegalStateException("No access token in response")
  }

  private fun loadPrivateKey(): PrivateKey {
    val pemContent = when {
      !properties.privateKeyBase64.isNullOrBlank() -> {
        log.info("Loading GitHub App private key from base64")
        // Base64 decodes to PEM content (e.g., from `cat key.pem | base64`)
        String(Base64.getDecoder().decode(properties.privateKeyBase64), Charsets.UTF_8)
      }
      !properties.privateKeyPath.isNullOrBlank() -> {
        log.info("Loading GitHub App private key from file: {}", properties.privateKeyPath)
        Files.readString(Paths.get(properties.privateKeyPath))
      }
      else -> {
        throw IllegalStateException("GitHub App private key not configured. Set either privateKeyPath or privateKeyBase64.")
      }
    }

    return parsePemKey(pemContent)
  }

  private fun parsePemKey(pemContent: String): PrivateKey {
    val parser = PEMParser(StringReader(pemContent))
    val pemObject = parser.readObject()
      ?: throw IllegalStateException("Failed to parse PEM content")
    parser.close()

    val converter = JcaPEMKeyConverter()

    return when (pemObject) {
      // PKCS#1 format: BEGIN RSA PRIVATE KEY
      is PEMKeyPair -> converter.getPrivateKey(pemObject.privateKeyInfo)
      // PKCS#8 format: BEGIN PRIVATE KEY
      is PrivateKeyInfo -> converter.getPrivateKey(pemObject)
      else -> throw IllegalStateException("Unsupported PEM object type: ${pemObject::class.java}")
    }
  }

  private data class CachedToken(val token: String, val expiresAt: Instant) {
    fun isExpired() = Instant.now().isAfter(expiresAt)
  }

  private data class InstallationResponse(
    val id: Long,
  )

  private data class AccessTokenResponse(
    val token: String,
  )
}
