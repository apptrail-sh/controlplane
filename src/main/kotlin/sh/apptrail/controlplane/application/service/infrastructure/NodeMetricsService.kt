package sh.apptrail.controlplane.application.service.infrastructure

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.alerting.prometheus.PrometheusClient
import sh.apptrail.controlplane.infrastructure.config.NodeMetricsProperties
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

data class NodeMetrics(
  val cpuUtilizationPercent: Double?,
  val memoryUtilizationPercent: Double?,
  val lastUpdated: Instant?,
)

data class NodeMetricsResponse(
  val nodeName: String,
  val clusterId: Long,
  val metrics: NodeMetrics,
  val metricsEnabled: Boolean,
)

@Service
@EnableConfigurationProperties(NodeMetricsProperties::class)
class NodeMetricsService(
  private val nodeMetricsProperties: NodeMetricsProperties,
  private val clusterRepository: ClusterRepository,
  private val prometheusClient: PrometheusClient?,
) {
  private val log = LoggerFactory.getLogger(NodeMetricsService::class.java)

  fun isEnabled(): Boolean = nodeMetricsProperties.enabled && prometheusClient != null

  fun getNodeMetrics(clusterId: Long, nodeName: String): NodeMetricsResponse {
    if (!isEnabled()) {
      return NodeMetricsResponse(
        nodeName = nodeName,
        clusterId = clusterId,
        metrics = NodeMetrics(
          cpuUtilizationPercent = null,
          memoryUtilizationPercent = null,
          lastUpdated = null,
        ),
        metricsEnabled = false,
      )
    }

    val cluster = clusterRepository.findById(clusterId).orElse(null)
    if (cluster == null) {
      log.warn("Cluster not found for node metrics: $clusterId")
      return NodeMetricsResponse(
        nodeName = nodeName,
        clusterId = clusterId,
        metrics = NodeMetrics(
          cpuUtilizationPercent = null,
          memoryUtilizationPercent = null,
          lastUpdated = null,
        ),
        metricsEnabled = true,
      )
    }

    val clusterName = cluster.name
    val cpuUtilization = queryCpuUtilization(clusterName, nodeName)
    val memoryUtilization = queryMemoryUtilization(clusterName, nodeName)

    return NodeMetricsResponse(
      nodeName = nodeName,
      clusterId = clusterId,
      metrics = NodeMetrics(
        cpuUtilizationPercent = cpuUtilization?.let { roundToTwoDecimals(it) },
        memoryUtilizationPercent = memoryUtilization?.let { roundToTwoDecimals(it) },
        lastUpdated = Instant.now(),
      ),
      metricsEnabled = true,
    )
  }

  private fun queryCpuUtilization(clusterName: String, nodeName: String): Double? {
    return try {
      val query = interpolateQuery(nodeMetricsProperties.cpuQuery, clusterName, nodeName)
      val result = prometheusClient!!.queryInstant(query)
      result?.value
    } catch (e: Exception) {
      log.warn("Failed to fetch CPU utilization for node $nodeName in cluster $clusterName: ${e.message}")
      null
    }
  }

  private fun queryMemoryUtilization(clusterName: String, nodeName: String): Double? {
    return try {
      val query = interpolateQuery(nodeMetricsProperties.memoryQuery, clusterName, nodeName)
      val result = prometheusClient!!.queryInstant(query)
      result?.value
    } catch (e: Exception) {
      log.warn("Failed to fetch memory utilization for node $nodeName in cluster $clusterName: ${e.message}")
      null
    }
  }

  private fun interpolateQuery(template: String, clusterName: String, nodeName: String): String {
    return template
      .replace("{{clusterLabel}}", nodeMetricsProperties.clusterLabel)
      .replace("{{nodeLabel}}", nodeMetricsProperties.nodeLabel)
      .replace("{{clusterName}}", clusterName)
      .replace("{{nodeName}}", nodeName)
  }

  private fun roundToTwoDecimals(value: Double): Double {
    if (value.isNaN() || value.isInfinite()) {
      return 0.0
    }
    return BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toDouble()
  }
}
