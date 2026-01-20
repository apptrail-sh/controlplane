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
  val shard: String?,
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
        shard = metric["shard"],
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
