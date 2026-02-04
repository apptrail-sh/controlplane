package sh.apptrail.controlplane.application.service.infrastructure

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.alerting.prometheus.PrometheusClient
import sh.apptrail.controlplane.infrastructure.config.PodMetricsProperties
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

data class PodMetrics(
  val cpuCores: Double?,
  val cpuLimitCores: Double?,
  val memoryBytes: Long?,
  val memoryLimitBytes: Long?,
  val lastUpdated: Instant?,
)

data class PodMetricsResponse(
  val podName: String,
  val namespace: String,
  val clusterId: Long,
  val metrics: PodMetrics,
  val metricsEnabled: Boolean,
)

@Service
@EnableConfigurationProperties(PodMetricsProperties::class)
class PodMetricsService(
  private val podMetricsProperties: PodMetricsProperties,
  private val clusterRepository: ClusterRepository,
  private val prometheusClient: PrometheusClient?,
) {
  private val log = LoggerFactory.getLogger(PodMetricsService::class.java)

  fun isEnabled(): Boolean = podMetricsProperties.enabled && prometheusClient != null

  fun getPodMetrics(clusterId: Long, namespace: String, podName: String): PodMetricsResponse {
    if (!isEnabled()) {
      return PodMetricsResponse(
        podName = podName,
        namespace = namespace,
        clusterId = clusterId,
        metrics = PodMetrics(
          cpuCores = null,
          cpuLimitCores = null,
          memoryBytes = null,
          memoryLimitBytes = null,
          lastUpdated = null,
        ),
        metricsEnabled = false,
      )
    }

    val cluster = clusterRepository.findById(clusterId).orElse(null)
    if (cluster == null) {
      log.warn("Cluster not found for pod metrics: $clusterId")
      return PodMetricsResponse(
        podName = podName,
        namespace = namespace,
        clusterId = clusterId,
        metrics = PodMetrics(
          cpuCores = null,
          cpuLimitCores = null,
          memoryBytes = null,
          memoryLimitBytes = null,
          lastUpdated = null,
        ),
        metricsEnabled = true,
      )
    }

    val clusterName = cluster.name
    val cpuCores = queryCpuUsage(clusterName, namespace, podName)
    val cpuLimitCores = queryCpuLimit(clusterName, namespace, podName)
    val memoryBytes = queryMemoryUsage(clusterName, namespace, podName)
    val memoryLimitBytes = queryMemoryLimit(clusterName, namespace, podName)

    return PodMetricsResponse(
      podName = podName,
      namespace = namespace,
      clusterId = clusterId,
      metrics = PodMetrics(
        cpuCores = cpuCores?.let { roundToFourDecimals(it) },
        cpuLimitCores = cpuLimitCores?.let { roundToFourDecimals(it) },
        memoryBytes = memoryBytes?.toLong(),
        memoryLimitBytes = memoryLimitBytes?.toLong(),
        lastUpdated = Instant.now(),
      ),
      metricsEnabled = true,
    )
  }

  private fun queryCpuUsage(clusterName: String, namespace: String, podName: String): Double? {
    return try {
      val query = interpolateQuery(podMetricsProperties.cpuQuery, clusterName, namespace, podName)
      val result = prometheusClient!!.queryInstant(query)
      result?.value
    } catch (e: Exception) {
      log.warn("Failed to fetch CPU usage for pod $podName in namespace $namespace, cluster $clusterName: ${e.message}")
      null
    }
  }

  private fun queryCpuLimit(clusterName: String, namespace: String, podName: String): Double? {
    return try {
      val query = interpolateQuery(podMetricsProperties.cpuLimitQuery, clusterName, namespace, podName)
      val result = prometheusClient!!.queryInstant(query)
      result?.value
    } catch (e: Exception) {
      log.warn("Failed to fetch CPU limit for pod $podName in namespace $namespace, cluster $clusterName: ${e.message}")
      null
    }
  }

  private fun queryMemoryUsage(clusterName: String, namespace: String, podName: String): Double? {
    return try {
      val query = interpolateQuery(podMetricsProperties.memoryQuery, clusterName, namespace, podName)
      val result = prometheusClient!!.queryInstant(query)
      result?.value
    } catch (e: Exception) {
      log.warn("Failed to fetch memory usage for pod $podName in namespace $namespace, cluster $clusterName: ${e.message}")
      null
    }
  }

  private fun queryMemoryLimit(clusterName: String, namespace: String, podName: String): Double? {
    return try {
      val query = interpolateQuery(podMetricsProperties.memoryLimitQuery, clusterName, namespace, podName)
      val result = prometheusClient!!.queryInstant(query)
      result?.value
    } catch (e: Exception) {
      log.warn("Failed to fetch memory limit for pod $podName in namespace $namespace, cluster $clusterName: ${e.message}")
      null
    }
  }

  private fun interpolateQuery(template: String, clusterName: String, namespace: String, podName: String): String {
    return template
      .replace("{{clusterLabel}}", podMetricsProperties.clusterLabel)
      .replace("{{namespaceLabel}}", podMetricsProperties.namespaceLabel)
      .replace("{{podLabel}}", podMetricsProperties.podLabel)
      .replace("{{clusterName}}", clusterName)
      .replace("{{namespace}}", namespace)
      .replace("{{podName}}", podName)
  }

  private fun roundToFourDecimals(value: Double): Double {
    if (value.isNaN() || value.isInfinite()) {
      return 0.0
    }
    return BigDecimal(value).setScale(4, RoundingMode.HALF_UP).toDouble()
  }
}
