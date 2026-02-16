package sh.apptrail.controlplane.web.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
import sh.apptrail.controlplane.application.service.auth.GoogleAuthCodeExchanger
import sh.apptrail.controlplane.application.service.auth.JwtTokenService
import sh.apptrail.controlplane.infrastructure.persistence.entity.UserEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.ApiKeyRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.UserPreferencesRepository
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
class PreferencesControllerIntegrationTests {

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
  private lateinit var userRepository: UserRepository

  @Autowired
  private lateinit var userPreferencesRepository: UserPreferencesRepository

  @Autowired
  private lateinit var apiKeyRepository: ApiKeyRepository

  private lateinit var client: RestClient

  private val httpClient = HttpClient.newHttpClient()

  @BeforeEach
  fun setUp() {
    userPreferencesRepository.deleteAll()
    apiKeyRepository.deleteAll()
    userRepository.deleteAll()
    client = RestClient.builder()
      .baseUrl("http://localhost:$port")
      .build()
  }

  private fun createTestUser(email: String = "test@example.com", sub: String = "google-sub-123"): UserEntity {
    return userRepository.save(
      UserEntity().apply {
        this.email = email
        provider = "google"
        providerSub = sub
        name = "Test User"
      }
    )
  }

  private fun rawGet(path: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
    val builder = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:$port$path"))
      .GET()
    headers.forEach { (k, v) -> builder.header(k, v) }
    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
  }

  private fun rawPut(path: String, body: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
    val builder = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:$port$path"))
      .header("Content-Type", "application/json")
      .PUT(HttpRequest.BodyPublishers.ofString(body))
    headers.forEach { (k, v) -> builder.header(k, v) }
    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
  }

  @Nested
  inner class `Authentication required` {

    @Test
    fun `GET preferences without token returns 401`() {
      val response = rawGet("/api/v1/preferences")
      assertThat(response.statusCode()).isEqualTo(401)
    }

    @Test
    fun `PUT preferences without token returns 401`() {
      val response = rawPut(
        "/api/v1/preferences",
        """{"pillLayout": "stacked", "theme": "dark"}""",
      )
      assertThat(response.statusCode()).isEqualTo(401)
    }
  }

  @Nested
  inner class `First-time user` {

    @Test
    fun `GET preferences returns 204 when no preferences exist`() {
      val user = createTestUser()
      val token = jwtTokenService.generateAccessToken(user)

      val response = rawGet(
        "/api/v1/preferences",
        mapOf("Authorization" to "Bearer $token"),
      )
      assertThat(response.statusCode()).isEqualTo(204)
    }
  }

  @Nested
  inner class `Save and retrieve preferences` {

    @Test
    fun `PUT then GET round-trip returns saved preferences`() {
      val user = createTestUser()
      val token = jwtTokenService.generateAccessToken(user)

      val putResponse = client.put()
        .uri("/api/v1/preferences")
        .header("Authorization", "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .body(mapOf("pillLayout" to "compact", "theme" to "light"))
        .retrieve()
        .body(Map::class.java)

      assertThat(putResponse).isNotNull
      assertThat(putResponse!!["pillLayout"]).isEqualTo("compact")
      assertThat(putResponse["theme"]).isEqualTo("light")

      val getResponse = client.get()
        .uri("/api/v1/preferences")
        .header("Authorization", "Bearer $token")
        .retrieve()
        .body(Map::class.java)

      assertThat(getResponse).isNotNull
      assertThat(getResponse!!["pillLayout"]).isEqualTo("compact")
      assertThat(getResponse["theme"]).isEqualTo("light")
    }

    @Test
    fun `PUT updates existing preferences idempotently`() {
      val user = createTestUser()
      val token = jwtTokenService.generateAccessToken(user)

      client.put()
        .uri("/api/v1/preferences")
        .header("Authorization", "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .body(mapOf("pillLayout" to "stacked", "theme" to "dark"))
        .retrieve()
        .body(Map::class.java)

      val updated = client.put()
        .uri("/api/v1/preferences")
        .header("Authorization", "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .body(mapOf("pillLayout" to "compact", "theme" to "system"))
        .retrieve()
        .body(Map::class.java)

      assertThat(updated).isNotNull
      assertThat(updated!!["pillLayout"]).isEqualTo("compact")
      assertThat(updated["theme"]).isEqualTo("system")

      // Verify only one row exists
      assertThat(userPreferencesRepository.count()).isEqualTo(1)
    }
  }

  @Nested
  inner class `User isolation` {

    @Test
    fun `users cannot see each other's preferences`() {
      val user1 = createTestUser("user1@example.com", "sub-1")
      val user2 = createTestUser("user2@example.com", "sub-2")
      val token1 = jwtTokenService.generateAccessToken(user1)
      val token2 = jwtTokenService.generateAccessToken(user2)

      client.put()
        .uri("/api/v1/preferences")
        .header("Authorization", "Bearer $token1")
        .contentType(MediaType.APPLICATION_JSON)
        .body(mapOf("pillLayout" to "compact", "theme" to "light"))
        .retrieve()
        .body(Map::class.java)

      client.put()
        .uri("/api/v1/preferences")
        .header("Authorization", "Bearer $token2")
        .contentType(MediaType.APPLICATION_JSON)
        .body(mapOf("pillLayout" to "stacked", "theme" to "dark"))
        .retrieve()
        .body(Map::class.java)

      val prefs1 = client.get()
        .uri("/api/v1/preferences")
        .header("Authorization", "Bearer $token1")
        .retrieve()
        .body(Map::class.java)

      val prefs2 = client.get()
        .uri("/api/v1/preferences")
        .header("Authorization", "Bearer $token2")
        .retrieve()
        .body(Map::class.java)

      assertThat(prefs1!!["pillLayout"]).isEqualTo("compact")
      assertThat(prefs1["theme"]).isEqualTo("light")
      assertThat(prefs2!!["pillLayout"]).isEqualTo("stacked")
      assertThat(prefs2["theme"]).isEqualTo("dark")
    }
  }
}
