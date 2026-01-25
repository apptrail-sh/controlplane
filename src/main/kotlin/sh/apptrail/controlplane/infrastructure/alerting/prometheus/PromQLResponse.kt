package sh.apptrail.controlplane.infrastructure.alerting.prometheus

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class PromQLResponse(
  val status: String,
  val data: PromQLData?,
  val errorType: String? = null,
  val error: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PromQLData(
  val resultType: String,
  val result: List<PromQLResult>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PromQLResult(
  val metric: Map<String, String>,
  val value: List<Any>?,
)

data class AlertInfo(
  val alertName: String,
  val severity: String?,
  val workload: String?,
  val workloadType: String?,
  val cluster: String?,
  val namespace: String?,
  val environment: String?,
  val cell: String?,
  val service: String?,
  val alertGroup: String?,
  val activeForSeconds: Long?,
) {
  companion object {
    fun fromPromQLResult(result: PromQLResult, activeForSecondsMap: Map<AlertKey, Long>? = null): AlertInfo {
      val metric = result.metric
      val key = AlertKey(
        alertName = metric["alertname"] ?: "unknown",
        workload = metric["workload"],
        cluster = metric["cluster"],
        namespace = metric["namespace"],
      )
      return AlertInfo(
        alertName = metric["alertname"] ?: "unknown",
        severity = metric["severity"],
        workload = metric["workload"],
        workloadType = metric["workload_type"],
        cluster = metric["cluster"],
        namespace = metric["namespace"],
        environment = metric["environment"],
        cell = metric["cell"],
        service = metric["service"],
        alertGroup = metric["alertgroup"],
        activeForSeconds = activeForSecondsMap?.get(key),
      )
    }
  }
}

data class AlertKey(
  val alertName: String,
  val workload: String?,
  val cluster: String?,
  val namespace: String?,
)

data class AlertAggregation(
  val bySeverity: Map<String, Int>,
  val byCluster: Map<String, Int>,
  val byAlertName: Map<String, Int>,
  val totalCount: Int,
  val criticalCount: Int,
  val warningCount: Int,
)

// Extended PromQL result for range queries (matrix results)
@JsonIgnoreProperties(ignoreUnknown = true)
data class PromQLRangeResult(
  val metric: Map<String, String>,
  val values: List<List<Any>>?, // [[timestamp, value], [timestamp, value], ...]
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PromQLRangeData(
  val resultType: String, // "matrix" for range queries
  val result: List<PromQLRangeResult>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PromQLRangeResponse(
  val status: String,
  val data: PromQLRangeData?,
  val errorType: String? = null,
  val error: String? = null,
)

// Simplified metric result types for the metrics service
data class MetricInstantResult(
  val value: Double?,
  val timestamp: Long?,
)

data class SparklinePoint(
  val timestamp: Long,
  val value: Double,
)

data class MetricRangeResult(
  val points: List<SparklinePoint>,
)
