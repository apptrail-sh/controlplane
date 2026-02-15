package sh.apptrail.controlplane.application.service.auth

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sh.apptrail.controlplane.infrastructure.persistence.entity.ApiKeyEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.ApiKeyRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

@Service
@ConditionalOnProperty(prefix = "apptrail.auth", name = ["enabled"], havingValue = "true")
class ApiKeyService(
  private val apiKeyRepository: ApiKeyRepository,
) {

  companion object {
    private const val KEY_PREFIX = "apt_"
    private const val KEY_LENGTH = 48
    private val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray()
    private val secureRandom = SecureRandom()
  }

  @Transactional
  fun validateKey(rawKey: String): ApiKeyEntity? {
    val hash = hashKey(rawKey)
    val apiKey = apiKeyRepository.findByKeyHash(hash) ?: return null

    // Check expiration
    if (apiKey.expiresAt != null && apiKey.expiresAt!!.isBefore(Instant.now())) {
      return null
    }

    // Update last used (fire and forget)
    apiKey.lastUsedAt = Instant.now()
    apiKeyRepository.save(apiKey)

    return apiKey
  }

  @Transactional
  fun createKey(name: String, description: String? = null): Pair<String, ApiKeyEntity> {
    val rawKey = generateRawKey()
    val hash = hashKey(rawKey)
    val prefix = rawKey.take(12)

    val entity = ApiKeyEntity().apply {
      this.name = name
      this.keyHash = hash
      this.keyPrefix = prefix
      this.description = description
    }

    val saved = apiKeyRepository.save(entity)
    return rawKey to saved
  }

  fun listKeys(): List<ApiKeyEntity> {
    return apiKeyRepository.findAll()
  }

  @Transactional
  fun deleteKey(id: Long): Boolean {
    if (!apiKeyRepository.existsById(id)) return false
    apiKeyRepository.deleteById(id)
    return true
  }

  @Transactional
  fun createKeyWithRawKey(rawKey: String, name: String, description: String? = null): ApiKeyEntity {
    val hash = hashKey(rawKey)
    val prefix = rawKey.take(12)

    val entity = ApiKeyEntity().apply {
      this.name = name
      this.keyHash = hash
      this.keyPrefix = prefix
      this.description = description
    }

    return apiKeyRepository.save(entity)
  }

  fun existsByHash(rawKey: String): Boolean {
    return apiKeyRepository.findByKeyHash(hashKey(rawKey)) != null
  }

  private fun generateRawKey(): String {
    val sb = StringBuilder(KEY_PREFIX)
    repeat(KEY_LENGTH) {
      sb.append(CHARS[secureRandom.nextInt(CHARS.size)])
    }
    return sb.toString()
  }

  private fun hashKey(rawKey: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(rawKey.toByteArray())
    return hashBytes.joinToString("") { "%02x".format(it) }
  }
}
