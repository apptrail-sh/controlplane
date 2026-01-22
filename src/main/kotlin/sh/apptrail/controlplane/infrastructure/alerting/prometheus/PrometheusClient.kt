package sh.apptrail.controlplane.infrastructure.alerting.prometheus

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
@ConditionalOnProperty(prefix = "app.alerting.prometheus", name = ["enabled"], havingValue = "true")
class PrometheusClient(
  private val properties: PrometheusProperties,
) {
  private val log = LoggerFactory.getLogger(PrometheusClient::class.java)

  private val restClient: RestClient = RestClient.builder()
    .requestFactory(SimpleClientHttpRequestFactory().apply {
      setConnectTimeout(properties.timeoutMs.toInt())
      setReadTimeout(properties.timeoutMs.toInt())
    })
    .build()

  fun queryFiringAlerts(): List<AlertInfo> {
    return try {
      val alertsResponse = executeQuery("""ALERTS{alertstate="firing"}""")
      val activeForMap = queryAlertDurations()

      alertsResponse?.data?.result?.map { result ->
        AlertInfo.fromPromQLResult(result, activeForMap)
      } ?: emptyList()
    } catch (e: Exception) {
      log.warn("Failed to query firing alerts from Prometheus: ${e.message}")
      emptyList()
    }
  }

  fun queryAlertsForWorkload(
    workload: String,
    workloadType: String,
    cluster: String,
    namespace: String,
  ): List<AlertInfo> {
    return try {
      val query = """ALERTS{workload="$workload", workload_type="$workloadType", cluster="$cluster", namespace="$namespace", alertstate="firing"}"""
      val alertsResponse = executeQuery(query)
      val activeForMap = queryAlertDurationsForWorkload(workload, workloadType, cluster, namespace)

      alertsResponse?.data?.result?.map { result ->
        AlertInfo.fromPromQLResult(result, activeForMap)
      } ?: emptyList()
    } catch (e: Exception) {
      log.warn("Failed to query alerts for workload $workload in $cluster/$namespace: ${e.message}")
      emptyList()
    }
  }

  fun queryAlertCountsBySeverity(): Map<String, Int> {
    return try {
      val query = """count(ALERTS{alertstate="firing"}) by (severity)"""
      val response = executeQuery(query)
      parseAggregationResult(response, "severity")
    } catch (e: Exception) {
      log.warn("Failed to query alert counts by severity: ${e.message}")
      emptyMap()
    }
  }

  fun queryAlertCountsByCluster(): Map<String, Int> {
    return try {
      val query = """count(ALERTS{alertstate="firing"}) by (cluster)"""
      val response = executeQuery(query)
      parseAggregationResult(response, "cluster")
    } catch (e: Exception) {
      log.warn("Failed to query alert counts by cluster: ${e.message}")
      emptyMap()
    }
  }

  fun queryAlertCountsByName(): Map<String, Int> {
    return try {
      val query = """count(ALERTS{alertstate="firing"}) by (alertname)"""
      val response = executeQuery(query)
      parseAggregationResult(response, "alertname")
    } catch (e: Exception) {
      log.warn("Failed to query alert counts by name: ${e.message}")
      emptyMap()
    }
  }

  fun queryCriticalAlerts(): List<AlertInfo> {
    return try {
      val query = """ALERTS{alertstate="firing", severity="critical"}"""
      val alertsResponse = executeQuery(query)
      val activeForMap = queryAlertDurations()

      alertsResponse?.data?.result?.map { result ->
        AlertInfo.fromPromQLResult(result, activeForMap)
      } ?: emptyList()
    } catch (e: Exception) {
      log.warn("Failed to query critical alerts: ${e.message}")
      emptyList()
    }
  }

  fun queryRecentAlertCountsForWorkloads(): Map<WorkloadInstanceKey, Int> {
    return try {
      val query = """
        count by (workload, workload_type, cluster, namespace) (
          max_over_time(ALERTS{alertstate="firing", workload!=""}[24h])
        )
      """.trimIndent()
      val response = executeQuery(query)
      parseWorkloadAlertCounts(response)
    } catch (e: Exception) {
      log.warn("Failed to query recent alert counts for workloads: ${e.message}")
      emptyMap()
    }
  }

  private fun parseWorkloadAlertCounts(response: PromQLResponse?): Map<WorkloadInstanceKey, Int> {
    return response?.data?.result?.mapNotNull { result ->
      val metric = result.metric
      val workload = metric["workload"] ?: return@mapNotNull null
      val workloadType = metric["workload_type"] ?: return@mapNotNull null
      val cluster = metric["cluster"] ?: return@mapNotNull null
      val namespace = metric["namespace"] ?: return@mapNotNull null
      val count = (result.value?.getOrNull(1) as? String)?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null

      WorkloadInstanceKey(
        workload = workload,
        workloadType = workloadType,
        cluster = cluster,
        namespace = namespace,
      ) to count
    }?.toMap() ?: emptyMap()
  }

  private fun parseAggregationResult(response: PromQLResponse?, groupByLabel: String): Map<String, Int> {
    return response?.data?.result?.mapNotNull { result ->
      val labelValue = result.metric[groupByLabel] ?: return@mapNotNull null
      val count = (result.value?.getOrNull(1) as? String)?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
      labelValue to count
    }?.toMap() ?: emptyMap()
  }

  private fun queryAlertDurations(): Map<AlertKey, Long> {
    return try {
      val response = executeQuery("ALERTS_FOR_STATE")
      parseAlertDurations(response)
    } catch (e: Exception) {
      log.warn("Failed to query alert durations: ${e.message}")
      emptyMap()
    }
  }

  private fun queryAlertDurationsForWorkload(
    workload: String,
    workloadType: String,
    cluster: String,
    namespace: String,
  ): Map<AlertKey, Long> {
    return try {
      val query = """ALERTS_FOR_STATE{workload="$workload", workload_type="$workloadType", cluster="$cluster", namespace="$namespace"}"""
      val response = executeQuery(query)
      parseAlertDurations(response)
    } catch (e: Exception) {
      log.warn("Failed to query alert durations for workload: ${e.message}")
      emptyMap()
    }
  }

  private fun parseAlertDurations(response: PromQLResponse?): Map<AlertKey, Long> {
    val now = System.currentTimeMillis() / 1000
    return response?.data?.result?.mapNotNull { result ->
      val metric = result.metric
      val alertStartTime = (result.value?.getOrNull(1) as? String)?.toDoubleOrNull()?.toLong()
      if (alertStartTime != null) {
        val key = AlertKey(
          alertName = metric["alertname"] ?: "unknown",
          workload = metric["workload"],
          cluster = metric["cluster"],
          namespace = metric["namespace"],
        )
        key to (now - alertStartTime)
      } else {
        null
      }
    }?.toMap() ?: emptyMap()
  }

  private fun executeQuery(query: String): PromQLResponse? {
    return try {
      val uri = java.net.URI.create(
        "${properties.baseUrl}${properties.queryPath}?query=${java.net.URLEncoder.encode(query, Charsets.UTF_8)}"
      )
      restClient.get()
        .uri(uri)
        .retrieve()
        .body(PromQLResponse::class.java)
    } catch (e: RestClientException) {
      log.warn("Prometheus query failed: ${e.message}")
      null
    }
  }

  /**
   * Execute an instant query and return the scalar result.
   * Used for getting current metric values.
   */
  fun queryInstant(query: String): MetricInstantResult? {
    return try {
      val response = executeQuery(query)
      val results = response?.data?.result
      if (response?.status != "success" || results.isNullOrEmpty()) {
        return null
      }

      val result = results.first()
      val value = result.value

      if (value != null && value.size >= 2) {
        val timestamp = (value[0] as? Number)?.toLong()
        val metricValue = (value[1] as? String)?.toDoubleOrNull()
        MetricInstantResult(value = metricValue, timestamp = timestamp)
      } else {
        null
      }
    } catch (e: Exception) {
      log.warn("Prometheus instant query failed: ${e.message}")
      null
    }
  }

  /**
   * Execute a range query and return time series data.
   * Used for generating sparklines.
   */
  fun queryRange(query: String, range: String, step: String): MetricRangeResult? {
    return try {
      val now = System.currentTimeMillis() / 1000
      val start = now - parseRangeToSeconds(range)

      val rangeQueryPath = properties.queryPath.replace("/query", "/query_range")
      val uri = java.net.URI.create(
        "${properties.baseUrl}${rangeQueryPath}?query=${java.net.URLEncoder.encode(query, Charsets.UTF_8)}" +
          "&start=$start&end=$now&step=$step"
      )

      val response = restClient.get()
        .uri(uri)
        .retrieve()
        .body(PromQLRangeResponse::class.java)

      val results = response?.data?.result
      if (response?.status != "success" || results.isNullOrEmpty()) {
        return null
      }

      val result = results.first()
      val points = result.values?.mapNotNull { point ->
        if (point.size >= 2) {
          val timestamp = (point[0] as? Number)?.toLong() ?: return@mapNotNull null
          val value = (point[1] as? String)?.toDoubleOrNull() ?: return@mapNotNull null
          SparklinePoint(timestamp = timestamp, value = value)
        } else {
          null
        }
      } ?: emptyList()

      MetricRangeResult(points = points)
    } catch (e: Exception) {
      log.warn("Prometheus range query failed: ${e.message}")
      null
    }
  }

  private fun parseRangeToSeconds(range: String): Long {
    val regex = Regex("""(\d+)([smhd])""")
    val match = regex.find(range) ?: return 3600 // Default 1h

    val value = match.groupValues[1].toLongOrNull() ?: return 3600
    val unit = match.groupValues[2]

    return when (unit) {
      "s" -> value
      "m" -> value * 60
      "h" -> value * 3600
      "d" -> value * 86400
      else -> 3600
    }
  }
}

data class WorkloadInstanceKey(
  val workload: String,
  val workloadType: String,
  val cluster: String,
  val namespace: String,
)
