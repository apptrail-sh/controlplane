package sh.apptrail.controlplane.application.service

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.config.ClusterEnvironmentProperties
import sh.apptrail.controlplane.infrastructure.config.QuickLinksProperties

data class QuickLinkContext(
  val clusterName: String,
  val clusterId: Long? = null,
  val namespace: String,
  val environment: String,
  val shard: String? = null,
  val workloadName: String,
  val workloadKind: String,
  val team: String? = null,
  val version: String? = null,
)

data class InterpolatedQuickLink(
  val name: String,
  val description: String?,
  val url: String,
  val linkType: String,
  val icon: String?,
)

data class QuickLinkTemplate(
  val name: String,
  val description: String?,
  val urlTemplate: String,
  val linkType: String,
  val icon: String?,
)

@Service
@EnableConfigurationProperties(QuickLinksProperties::class, ClusterEnvironmentProperties::class)
class QuickLinkService(
  private val quickLinksProperties: QuickLinksProperties,
  private val clusterEnvironmentProperties: ClusterEnvironmentProperties,
) {

  fun getAllTemplates(): List<QuickLinkTemplate> {
    return quickLinksProperties.links.map { config ->
      QuickLinkTemplate(
        name = config.name,
        description = config.description,
        urlTemplate = config.urlTemplate,
        linkType = config.linkType,
        icon = config.icon,
      )
    }
  }

  fun getInterpolatedLinks(context: QuickLinkContext): List<InterpolatedQuickLink> {
    val environmentMetadata = getEnvironmentMetadata(context.environment)

    return quickLinksProperties.links.map { config ->
      InterpolatedQuickLink(
        name = config.name,
        description = config.description,
        url = interpolateTemplate(config.urlTemplate, context, environmentMetadata),
        linkType = config.linkType,
        icon = config.icon,
      )
    }
  }

  private fun getEnvironmentMetadata(environmentName: String): Map<String, String> {
    return clusterEnvironmentProperties.environments
      .find { it.name == environmentName }
      ?.metadata
      ?: emptyMap()
  }

  private fun interpolateTemplate(
    template: String,
    context: QuickLinkContext,
    environmentMetadata: Map<String, String>,
  ): String {
    var result = template

    // Cluster variables
    result = result.replace("{{cluster.name}}", context.clusterName)
    result = result.replace("{{cluster.id}}", context.clusterId?.toString() ?: "")

    // Instance variables
    result = result.replace("{{instance.namespace}}", context.namespace)
    result = result.replace("{{instance.environment}}", context.environment)
    result = result.replace("{{instance.shard}}", context.shard ?: "")

    // Workload variables
    result = result.replace("{{workload.name}}", context.workloadName)
    result = result.replace("{{workload.kind}}", context.workloadKind)
    result = result.replace("{{workload.team}}", context.team ?: "")

    // Version
    result = result.replace("{{version}}", context.version ?: "")

    // Environment metadata (e.g., {{environment.metadata.gcp-project}})
    val metadataPattern = Regex("""\{\{environment\.metadata\.([^}]+)\}\}""")
    result = metadataPattern.replace(result) { matchResult ->
      val key = matchResult.groupValues[1]
      environmentMetadata[key] ?: ""
    }

    return result
  }
}
