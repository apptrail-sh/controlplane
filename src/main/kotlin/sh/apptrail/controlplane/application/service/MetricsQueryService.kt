package sh.apptrail.controlplane.application.service

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.config.ClusterEnvironmentProperties
import sh.apptrail.controlplane.infrastructure.config.MetricCategory
import sh.apptrail.controlplane.infrastructure.config.MetricUnit
import sh.apptrail.controlplane.infrastructure.config.MetricsQueriesProperties

data class MetricQueryContext(
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

data class InterpolatedMetricQuery(
  val id: String,
  val name: String,
  val description: String?,
  val query: String,
  val unit: MetricUnit,
  val category: MetricCategory,
  val sparklineRange: String,
  val sparklineStep: String,
)

data class MetricQueryTemplate(
  val id: String,
  val name: String,
  val description: String?,
  val queryTemplate: String,
  val unit: MetricUnit,
  val category: MetricCategory,
  val sparklineRange: String,
  val sparklineStep: String,
)

@Service
@EnableConfigurationProperties(MetricsQueriesProperties::class, ClusterEnvironmentProperties::class)
class MetricsQueryService(
  private val metricsQueriesProperties: MetricsQueriesProperties,
  private val clusterEnvironmentProperties: ClusterEnvironmentProperties,
) {

  fun isEnabled(): Boolean = metricsQueriesProperties.enabled

  fun isSparklinesEnabled(): Boolean = metricsQueriesProperties.sparklinesEnabled

  fun getQueryTemplates(): List<MetricQueryTemplate> {
    return metricsQueriesProperties.queries.map { config ->
      MetricQueryTemplate(
        id = config.id,
        name = config.name,
        description = config.description,
        queryTemplate = config.query,
        unit = config.unit,
        category = config.category,
        sparklineRange = config.sparklineRange,
        sparklineStep = config.sparklineStep,
      )
    }
  }

  fun getInterpolatedQueries(context: MetricQueryContext): List<InterpolatedMetricQuery> {
    val environmentMetadata = getEnvironmentMetadata(context.environment)

    return metricsQueriesProperties.queries.map { config ->
      InterpolatedMetricQuery(
        id = config.id,
        name = config.name,
        description = config.description,
        query = interpolateTemplate(config.query, context, environmentMetadata),
        unit = config.unit,
        category = config.category,
        sparklineRange = config.sparklineRange,
        sparklineStep = config.sparklineStep,
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
    context: MetricQueryContext,
    environmentMetadata: Map<String, String>,
  ): String {
    // Build variable map for lookups
    val variables = mapOf(
      "cluster.name" to context.clusterName,
      "cluster.id" to (context.clusterId?.toString() ?: ""),
      "namespace" to context.namespace,
      "environment" to context.environment,
      "shard" to (context.shard ?: ""),
      "workload.name" to context.workloadName,
      "workload.kind" to context.workloadKind,
      "team" to (context.team ?: ""),
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
