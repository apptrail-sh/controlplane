package sh.apptrail.controlplane.web.controller

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.application.service.auth.ApiKeyService
import java.time.Instant

@RestController
@RequestMapping("/api/v1/api-keys")
@ConditionalOnProperty(prefix = "apptrail.auth", name = ["enabled"], havingValue = "true")
class ApiKeyController(
  private val apiKeyService: ApiKeyService,
) {

  @PostMapping
  fun createApiKey(@RequestBody request: CreateApiKeyRequest): ResponseEntity<CreateApiKeyResponse> {
    val (rawKey, entity) = apiKeyService.createKey(request.name, request.description)
    return ResponseEntity.status(HttpStatus.CREATED).body(
      CreateApiKeyResponse(
        id = entity.id!!,
        name = entity.name,
        key = rawKey,
        keyPrefix = entity.keyPrefix,
        description = entity.description,
        createdAt = entity.createdAt!!,
      )
    )
  }

  @GetMapping
  fun listApiKeys(): List<ApiKeyListResponse> {
    return apiKeyService.listKeys().map { entity ->
      ApiKeyListResponse(
        id = entity.id!!,
        name = entity.name,
        keyPrefix = entity.keyPrefix,
        description = entity.description,
        lastUsedAt = entity.lastUsedAt,
        expiresAt = entity.expiresAt,
        createdAt = entity.createdAt!!,
      )
    }
  }

  @DeleteMapping("/{id}")
  fun deleteApiKey(@PathVariable id: Long): ResponseEntity<Void> {
    return if (apiKeyService.deleteKey(id)) {
      ResponseEntity.noContent().build()
    } else {
      ResponseEntity.notFound().build()
    }
  }
}

data class CreateApiKeyRequest(
  val name: String,
  val description: String? = null,
)

data class CreateApiKeyResponse(
  val id: Long,
  val name: String,
  val key: String,
  val keyPrefix: String,
  val description: String?,
  val createdAt: Instant,
)

data class ApiKeyListResponse(
  val id: Long,
  val name: String,
  val keyPrefix: String,
  val description: String?,
  val lastUsedAt: Instant?,
  val expiresAt: Instant?,
  val createdAt: Instant,
)
