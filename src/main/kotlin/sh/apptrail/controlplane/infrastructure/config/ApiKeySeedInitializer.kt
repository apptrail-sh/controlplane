package sh.apptrail.controlplane.infrastructure.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import sh.apptrail.controlplane.application.service.auth.ApiKeyService

@Component
@ConditionalOnProperty(prefix = "apptrail.auth", name = ["enabled"], havingValue = "true")
class ApiKeySeedInitializer(
  private val apiKeyService: ApiKeyService,
  private val authProperties: AuthProperties,
) : ApplicationRunner {

  private val log = LoggerFactory.getLogger(ApiKeySeedInitializer::class.java)

  override fun run(args: ApplicationArguments) {
    val seedKey = authProperties.seedApiKey
    if (seedKey.isNullOrBlank()) return

    if (apiKeyService.existsByHash(seedKey)) {
      log.info("Seed API key already exists, skipping creation")
      return
    }

    // Create the seed API key with the exact raw key provided
    apiKeyService.createKeyWithRawKey(seedKey, "seed", "Auto-created seed API key")
    log.warn("Seed API key created - consider rotating in production")
  }
}
