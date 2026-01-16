package sh.apptrail.controlplane.infrastructure.metrics

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.time.Instant

@Component
@ConditionalOnBean(name = ["prometheusRestClient"])
class PrometheusClient(
  private val prometheusRestClient: RestClient
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun queryRange(
    query: String,
    start: Instant,
    end: Instant,
    step: String = "1m"
  ): QueryRangeResult? {
    return try {
      log.debug("Executing Prometheus range query: {}", query)

      val response = prometheusRestClient.get()
        .uri { uriBuilder ->
          uriBuilder
            .path("/query_range")
            .queryParam("query", query)
            .queryParam("start", start.epochSecond)
            .queryParam("end", end.epochSecond)
            .queryParam("step", step)
            .build()
        }
        .retrieve()
        .body<PrometheusResponse>()

      if (response?.status == "success" && response.data != null) {
        val values = response.data.result.flatMap { r ->
          r.values?.mapNotNull { pair ->
            pair.getOrNull(1)?.toDoubleOrNull()
          } ?: emptyList()
        }
        QueryRangeResult(values)
      } else {
        log.warn("Prometheus query returned status: {}", response?.status)
        null
      }
    } catch (e: Exception) {
      log.error("Error executing Prometheus range query", e)
      null
    }
  }

  fun queryInstant(query: String, time: Instant): Double? {
    return try {
      log.debug("Executing Prometheus instant query: {}", query)

      val response = prometheusRestClient.get()
        .uri { uriBuilder ->
          uriBuilder
            .path("/query")
            .queryParam("query", query)
            .queryParam("time", time.epochSecond)
            .build()
        }
        .retrieve()
        .body<PrometheusResponse>()

      if (response?.status == "success" && response.data != null) {
        response.data.result.firstOrNull()?.value?.getOrNull(1)?.toDoubleOrNull()
      } else {
        log.warn("Prometheus instant query returned status: {}", response?.status)
        null
      }
    } catch (e: Exception) {
      log.error("Error executing Prometheus instant query", e)
      null
    }
  }
}

data class QueryRangeResult(
  val values: List<Double>
) {
  fun average(): Double? = values.takeIf { it.isNotEmpty() }?.average()
  fun max(): Double? = values.maxOrNull()
  fun min(): Double? = values.minOrNull()
}

data class PrometheusResponse(
  val status: String,
  val data: PrometheusData?,
  val errorType: String? = null,
  val error: String? = null
)

data class PrometheusData(
  @JsonProperty("resultType")
  val resultType: String,
  val result: List<PrometheusResult>
)

data class PrometheusResult(
  val metric: Map<String, String>? = null,
  val value: List<String>? = null,
  val values: List<List<String>>? = null
)
