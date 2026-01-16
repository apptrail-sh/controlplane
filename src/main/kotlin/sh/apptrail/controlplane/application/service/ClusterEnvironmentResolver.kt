package sh.apptrail.controlplane.application.service

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.config.ClusterEnvironmentProperties

@Service
@EnableConfigurationProperties(ClusterEnvironmentProperties::class)
class ClusterEnvironmentResolver(
  private val properties: ClusterEnvironmentProperties,
) {
  fun resolveEnvironment(clusterId: String): String {
    return properties.environments[clusterId] ?: "unknown"
  }
}
