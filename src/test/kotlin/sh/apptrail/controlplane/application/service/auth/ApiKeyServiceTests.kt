package sh.apptrail.controlplane.application.service.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import sh.apptrail.controlplane.infrastructure.persistence.entity.ApiKeyEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.ApiKeyRepository
import java.time.Instant

class ApiKeyServiceTests {

  private val apiKeyRepository: ApiKeyRepository = mock()
  private val apiKeyService = ApiKeyService(apiKeyRepository)

  @Test
  fun `createKey should generate key with apt_ prefix and store hash`() {
    val captor = argumentCaptor<ApiKeyEntity>()
    whenever(apiKeyRepository.save(captor.capture())).thenAnswer { captor.lastValue.apply { id = 1L } }

    val (rawKey, entity) = apiKeyService.createKey("test-key", "A test key")

    assertThat(rawKey).startsWith("apt_")
    assertThat(rawKey).hasSize(4 + 48) // prefix + random chars
    assertThat(entity.name).isEqualTo("test-key")
    assertThat(entity.description).isEqualTo("A test key")
    assertThat(entity.keyPrefix).isEqualTo(rawKey.take(12))
    assertThat(entity.keyHash).isNotBlank()
    assertThat(entity.keyHash).isNotEqualTo(rawKey) // hash, not plaintext
  }

  @Test
  fun `validateKey should return entity for valid key`() {
    val entity = ApiKeyEntity().apply {
      id = 1L
      name = "test"
      keyHash = "somehash"
      keyPrefix = "apt_test1234"
    }
    whenever(apiKeyRepository.findByKeyHash(any())).thenReturn(entity)
    whenever(apiKeyRepository.save(any())).thenReturn(entity)

    val result = apiKeyService.validateKey("apt_testkey")
    assertThat(result).isNotNull
    assertThat(result!!.name).isEqualTo("test")
  }

  @Test
  fun `validateKey should return null for unknown key`() {
    whenever(apiKeyRepository.findByKeyHash(any())).thenReturn(null)

    val result = apiKeyService.validateKey("apt_unknownkey")
    assertThat(result).isNull()
  }

  @Test
  fun `validateKey should return null for expired key`() {
    val entity = ApiKeyEntity().apply {
      id = 1L
      name = "expired"
      keyHash = "somehash"
      keyPrefix = "apt_expired1"
      expiresAt = Instant.now().minusSeconds(3600) // expired 1 hour ago
    }
    whenever(apiKeyRepository.findByKeyHash(any())).thenReturn(entity)

    val result = apiKeyService.validateKey("apt_expiredkey")
    assertThat(result).isNull()
    verify(apiKeyRepository, never()).save(any())
  }
}
