package sh.apptrail.controlplane.web.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.client.RestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import sh.apptrail.controlplane.application.service.auth.ApiKeyService
import sh.apptrail.controlplane.application.service.auth.GoogleClaims
import sh.apptrail.controlplane.application.service.auth.GoogleAuthCodeExchanger
import sh.apptrail.controlplane.application.service.auth.JwtTokenService
import sh.apptrail.controlplane.infrastructure.persistence.entity.UserEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.ApiKeyRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.UserRepository
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(
  properties = [
    "apptrail.auth.enabled=true",
    "apptrail.auth.jwt.secret=test-secret-that-is-long-enough-for-hmac-sha-256-key-testing",
    "apptrail.auth.google.client-id=test-google-client-id",
    "apptrail.auth.allowed-domains=example.com",
  ]
)
class AuthControllerIntegrationTests {

  companion object {
    @Container
    @ServiceConnection
    @JvmStatic
    val postgres = PostgreSQLContainer("postgres:18-alpine")
  }

  @LocalServerPort
  private var port: Int = 0

  @MockitoBean
  private lateinit var googleAuthCodeExchanger: GoogleAuthCodeExchanger

  @Autowired
  private lateinit var jwtTokenService: JwtTokenService

  @Autowired
  private lateinit var apiKeyService: ApiKeyService

  @Autowired
  private lateinit var userRepository: UserRepository

  @Autowired
  private lateinit var apiKeyRepository: ApiKeyRepository

  private lateinit var client: RestClient

  @BeforeEach
  fun setUp() {
    apiKeyRepository.deleteAll()
    userRepository.deleteAll()
    client = RestClient.builder()
      .baseUrl("http://localhost:$port")
      .build()
  }

  private fun createTestUser(): UserEntity {
    return userRepository.save(
      UserEntity().apply {
        email = "test@example.com"
        provider = "google"
        providerSub = "google-sub-123"
        name = "Test User"
      }
    )
  }

  private val httpClient = HttpClient.newHttpClient()

  /** Use raw Java HttpClient for requests expecting 401, since Jetty's client intercepts 401 */
  private fun rawGet(path: String, headers: Map<String, String> = emptyMap()): Int {
    val builder = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:$port$path"))
      .GET()
    headers.forEach { (k, v) -> builder.header(k, v) }
    val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    return response.statusCode()
  }

  private fun rawPost(path: String, body: String, headers: Map<String, String> = emptyMap()): Int {
    val builder = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:$port$path"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
    headers.forEach { (k, v) -> builder.header(k, v) }
    val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    return response.statusCode()
  }

  @Nested
  inner class `Auth status endpoint` {

    @Test
    fun `GET auth status returns enabled with google provider`() {
      val response = client.get()
        .uri("/api/v1/auth/status")
        .retrieve()
        .body(Map::class.java)

      assertThat(response).isNotNull
      assertThat(response!!["enabled"]).isEqualTo(true)
      @Suppress("UNCHECKED_CAST")
      val providers = response["providers"] as List<String>
      assertThat(providers).contains("google")
      assertThat(response["googleClientId"]).isEqualTo("test-google-client-id")
    }
  }

  @Nested
  inner class `Google login` {

    @Test
    fun `POST auth google returns tokens on valid login`() {
      whenever(googleAuthCodeExchanger.exchangeAuthCode(any())).thenReturn(
        GoogleClaims(
          sub = "google-sub-123",
          email = "test@example.com",
          name = "Test User",
          picture = "https://example.com/photo.jpg",
          emailVerified = true,
        )
      )

      val response = client.post()
        .uri("/api/v1/auth/google")
        .contentType(MediaType.APPLICATION_JSON)
        .body(mapOf("code" to "valid-auth-code"))
        .retrieve()
        .body(Map::class.java)

      assertThat(response).isNotNull
      assertThat(response!!["accessToken"]).isNotNull
      assertThat(response["refreshToken"]).isNotNull
      assertThat(response["expiresIn"]).isEqualTo(3600)
      assertThat(response["tokenType"]).isEqualTo("Bearer")
    }

    @Test
    fun `POST auth google rejects non-allowed domain`() {
      whenever(googleAuthCodeExchanger.exchangeAuthCode(any())).thenReturn(
        GoogleClaims(
          sub = "google-sub-456",
          email = "user@other-domain.com",
          name = "Other User",
          picture = null,
          emailVerified = true,
        )
      )

      val statusCode = rawPost(
        "/api/v1/auth/google",
        """{"code": "valid-auth-code-other-domain"}""",
      )
      assertThat(statusCode).isEqualTo(403)
    }
  }

  @Nested
  inner class `Protected endpoints` {

    @Test
    fun `GET workloads without token returns 401`() {
      val statusCode = rawGet("/api/v1/workloads")
      assertThat(statusCode).isEqualTo(401)
    }

    @Test
    fun `GET workloads with valid JWT returns 200`() {
      val user = createTestUser()
      val token = jwtTokenService.generateAccessToken(user)

      val response = client.get()
        .uri("/api/v1/workloads")
        .header("Authorization", "Bearer $token")
        .retrieve()
        .body(String::class.java)

      assertThat(response).isNotNull
    }

    @Test
    fun `POST ingest events without API key returns 401`() {
      val statusCode = rawPost("/ingest/v1/agent/events", "{}")
      assertThat(statusCode).isEqualTo(401)
    }

    @Test
    fun `POST ingest events with valid API key is not 401`() {
      val (rawKey, _) = apiKeyService.createKey("test-agent", "Test agent key")

      val body = """{
        "eventId": "test-event-1",
        "occurredAt": "2025-01-01T00:00:00Z",
        "source": {"clusterId": "test-cluster", "agentVersion": "1.0.0"},
        "workload": {"kind": "DEPLOYMENT", "name": "test-app", "namespace": "default"},
        "labels": {},
        "kind": "DEPLOYMENT",
        "revision": {"current": "v1.0.0"},
        "phase": "PROGRESSING"
      }"""

      // With valid API key, should get 202 (accepted) not 401
      val response = client.post()
        .uri("/ingest/v1/agent/events")
        .header("X-API-Key", rawKey)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity()

      assertThat(response.statusCode.value()).isEqualTo(202)
    }
  }

  @Nested
  inner class `Role-based access control` {

    @Test
    fun `API key cannot access api v1 workloads`() {
      val (rawKey, _) = apiKeyService.createKey("test-agent", "Test agent key")

      val statusCode = rawGet(
        "/api/v1/workloads",
        mapOf("X-API-Key" to rawKey),
      )
      assertThat(statusCode).isEqualTo(403)
    }

    @Test
    fun `API key can access ingest endpoint`() {
      val (rawKey, _) = apiKeyService.createKey("test-agent", "Test agent key")

      val body = """{
        "eventId": "test-event-rbac",
        "occurredAt": "2025-01-01T00:00:00Z",
        "source": {"clusterId": "test-cluster", "agentVersion": "1.0.0"},
        "workload": {"kind": "DEPLOYMENT", "name": "test-app", "namespace": "default"},
        "labels": {},
        "kind": "DEPLOYMENT",
        "revision": {"current": "v1.0.0"},
        "phase": "PROGRESSING"
      }"""

      val statusCode = rawPost(
        "/ingest/v1/agent/events",
        body,
        mapOf("X-API-Key" to rawKey),
      )
      assertThat(statusCode).isEqualTo(202)
    }
  }

  @Nested
  inner class `Refresh token rotation` {

    @Test
    fun `stale refresh token is rejected after rotation`() {
      val user = createTestUser()
      val oldRefreshToken = jwtTokenService.generateRefreshToken(user)

      // Use the refresh token once — this increments tokenVersion
      val firstRefreshStatus = rawPost(
        "/api/v1/auth/refresh",
        """{"refreshToken": "$oldRefreshToken"}""",
      )
      assertThat(firstRefreshStatus).isEqualTo(200)

      // Reuse the old refresh token — should be rejected
      val secondRefreshStatus = rawPost(
        "/api/v1/auth/refresh",
        """{"refreshToken": "$oldRefreshToken"}""",
      )
      assertThat(secondRefreshStatus).isEqualTo(401)
    }
  }

  @Nested
  inner class `Public endpoints` {

    @Test
    fun `GET health without auth returns 200`() {
      val response = client.get()
        .uri("/health")
        .retrieve()
        .body(String::class.java)

      assertThat(response).isNotNull
    }
  }
}
