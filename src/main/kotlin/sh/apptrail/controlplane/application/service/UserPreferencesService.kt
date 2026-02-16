package sh.apptrail.controlplane.application.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sh.apptrail.controlplane.infrastructure.persistence.entity.UserPreferencesEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.UserPreferencesRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.UserRepository

data class UserPreferences(
  val pillLayout: String? = null,
  val theme: String? = null,
)

@Service
@ConditionalOnProperty(prefix = "apptrail.auth", name = ["enabled"], havingValue = "true")
class UserPreferencesService(
  private val userPreferencesRepository: UserPreferencesRepository,
  private val userRepository: UserRepository,
) {

  fun getPreferences(userId: Long): UserPreferences? {
    val entity = userPreferencesRepository.findByUserId(userId) ?: return null
    return entity.toUserPreferences()
  }

  @Transactional
  fun savePreferences(userId: Long, prefs: UserPreferences): UserPreferences {
    val entity = userPreferencesRepository.findByUserId(userId)
      ?: UserPreferencesEntity().apply {
        user = userRepository.getReferenceById(userId)
      }

    entity.preferences = buildMap {
      prefs.pillLayout?.let { put("pillLayout", it) }
      prefs.theme?.let { put("theme", it) }
    }

    return userPreferencesRepository.save(entity).toUserPreferences()
  }

  private fun UserPreferencesEntity.toUserPreferences(): UserPreferences {
    return UserPreferences(
      pillLayout = preferences["pillLayout"] as? String,
      theme = preferences["theme"] as? String,
    )
  }
}
