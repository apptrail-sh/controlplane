package sh.apptrail.controlplane.web.controller

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import sh.apptrail.controlplane.application.service.UserPreferences
import sh.apptrail.controlplane.application.service.UserPreferencesService

@RestController
@RequestMapping("/api/v1/preferences")
@ConditionalOnProperty(prefix = "apptrail.auth", name = ["enabled"], havingValue = "true")
class PreferencesController(
  private val userPreferencesService: UserPreferencesService,
) {

  @GetMapping
  fun getPreferences(): ResponseEntity<PreferencesResponse> {
    val userId = getCurrentUserId() ?: return ResponseEntity.status(401).build()
    val prefs = userPreferencesService.getPreferences(userId)
      ?: return ResponseEntity.noContent().build()
    return ResponseEntity.ok(prefs.toResponse())
  }

  @PutMapping
  fun savePreferences(@RequestBody request: SavePreferencesRequest): ResponseEntity<PreferencesResponse> {
    val userId = getCurrentUserId() ?: return ResponseEntity.status(401).build()
    val prefs = userPreferencesService.savePreferences(
      userId,
      UserPreferences(
        pillLayout = request.pillLayout,
        theme = request.theme,
      )
    )
    return ResponseEntity.ok(prefs.toResponse())
  }

  private fun getCurrentUserId(): Long? {
    val auth = SecurityContextHolder.getContext().authentication ?: return null
    return (auth.principal as? String)?.toLongOrNull()
  }
}

data class SavePreferencesRequest(
  val pillLayout: String? = null,
  val theme: String? = null,
)

data class PreferencesResponse(
  val pillLayout: String?,
  val theme: String?,
)

private fun UserPreferences.toResponse() = PreferencesResponse(
  pillLayout = pillLayout,
  theme = theme,
)
