package sh.apptrail.controlplane.application.service

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.config.ClusterEnvironmentProperties
import sh.apptrail.controlplane.infrastructure.config.QuickLinkType
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
  val linkType: QuickLinkType,
  val icon: String?,
)

data class QuickLinkTemplate(
  val name: String,
  val description: String?,
  val urlTemplate: String,
  val linkType: QuickLinkType,
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
    // Build variable map for lookups
    val variables = mapOf(
      "cluster.name" to context.clusterName,
      "cluster.id" to (context.clusterId?.toString() ?: ""),
      "instance.namespace" to context.namespace,
      "instance.environment" to context.environment,
      "instance.shard" to (context.shard ?: ""),
      "workload.name" to context.workloadName,
      "workload.kind" to context.workloadKind,
      "workload.team" to (context.team ?: ""),
      "version" to (context.version ?: ""),
    )

    // Match {{variable}} or {{variable | function}}
    val pattern = Regex("""\{\{([^|}]+)(?:\|([^}]+))?\}\}""")

    return pattern.replace(template) { matchResult ->
      val variableName = matchResult.groupValues[1].trim()
      val functionName = matchResult.groupValues[2].takeIf { it.isNotEmpty() }

      val value = when {
        variableName.startsWith("environment.metadata.") -> {
          val key = variableName.removePrefix("environment.metadata.")
          environmentMetadata[key] ?: ""
        }
        else -> variables[variableName] ?: ""
      }

      if (functionName != null) {
        applyFunction(value, functionName)
      } else {
        value
      }
    }
  }

  private fun applyFunction(value: String, function: String): String {
    return when (function.trim().lowercase()) {
      "lowercase" -> value.lowercase()
      "uppercase" -> value.uppercase()
      else -> value // Unknown function, return unchanged
    }
  }
}
