package sh.apptrail.controlplane.application.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.alerting.prometheus.MetricInstantResult
import sh.apptrail.controlplane.infrastructure.alerting.prometheus.MetricRangeResult
import sh.apptrail.controlplane.infrastructure.alerting.prometheus.PrometheusClient
import sh.apptrail.controlplane.infrastructure.alerting.prometheus.PrometheusProperties
import sh.apptrail.controlplane.infrastructure.alerting.prometheus.SparklinePoint
import sh.apptrail.controlplane.infrastructure.config.MetricCategory
import sh.apptrail.controlplane.infrastructure.config.MetricUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

data class InstanceMetricsRequest(
  val clusterName: String,
  val clusterId: Long? = null,
  val namespace: String,
  val environment: String,
  val cell: String? = null,
  val workloadName: String,
  val workloadKind: String,
  val team: String? = null,
  val version: String? = null,
  val includeSparklines: Boolean = false,
)

data class FormattedMetricValue(
  val id: String,
  val name: String,
  val description: String?,
  val rawValue: Double?,
  val formattedValue: String?,
  val unit: MetricUnit,
  val category: MetricCategory,
  val sparkline: List<SparklinePoint>?,
)

data class InstanceMetricsResponse(
  val metrics: List<FormattedMetricValue>,
  val metricsEnabled: Boolean,
  val sparklinesEnabled: Boolean,
)

@Service
class InstanceMetricsService(
  private val metricsQueryService: MetricsQueryService,
  private val prometheusClient: PrometheusClient?,
  private val prometheusProperties: PrometheusProperties?,
) {
  private val log = LoggerFactory.getLogger(InstanceMetricsService::class.java)

  private data class CacheEntry(
    val response: InstanceMetricsResponse,
    val timestamp: Long,
  )
  // TODO add Valkey later
  private val cache = ConcurrentHashMap<String, CacheEntry>()
  private val executor = Executors.newVirtualThreadPerTaskExecutor()

  fun getMetricsStatus(): MetricsStatusResponse {
    return MetricsStatusResponse(
      metricsEnabled = metricsQueryService.isEnabled() && prometheusClient != null,
      sparklinesEnabled = metricsQueryService.isSparklinesEnabled(),
    )
  }

  fun getInstanceMetrics(request: InstanceMetricsRequest): InstanceMetricsResponse {
    val isEnabled = metricsQueryService.isEnabled() && prometheusClient != null
    val sparklinesEnabled = metricsQueryService.isSparklinesEnabled()

    if (!isEnabled) {
      return InstanceMetricsResponse(
        metrics = emptyList(),
        metricsEnabled = false,
        sparklinesEnabled = false,
      )
    }

    val includeSparklines = request.includeSparklines && sparklinesEnabled
    val cacheKey = buildCacheKey(request, includeSparklines)
    val cacheTtlMs = (prometheusProperties?.cache?.ttlSeconds ?: 30) * 1000

    val cached = cache[cacheKey]
    if (cached != null && prometheusProperties?.cache?.enabled != false) {
      if (System.currentTimeMillis() - cached.timestamp < cacheTtlMs) {
        return cached.response
      }
    }

    val context = MetricQueryContext(
      clusterName = request.clusterName,
      clusterId = request.clusterId,
      namespace = request.namespace,
      environment = request.environment,
      cell = request.cell,
      workloadName = request.workloadName,
      workloadKind = request.workloadKind,
      team = request.team,
      version = request.version,
    )

    val interpolatedQueries = metricsQueryService.getInterpolatedQueries(context)

    val startTime = System.nanoTime()

    val futures = interpolatedQueries.map { query ->
      executor.submit<FormattedMetricValue> {
        try {
          val queryStart = System.nanoTime()
          val instantResult = prometheusClient!!.queryInstant(query.query)
          val sparklineData = if (includeSparklines) {
            prometheusClient.queryRange(query.query, query.sparklineRange, query.sparklineStep)
          } else {
            null
          }
          log.debug("Metric [{}] took {}ms", query.id, (System.nanoTime() - queryStart) / 1_000_000)

          FormattedMetricValue(
            id = query.id,
            name = query.name,
            description = query.description,
            rawValue = instantResult?.value,
            formattedValue = formatValue(instantResult, query.unit),
            unit = query.unit,
            category = query.category,
            sparkline = sparklineData?.points,
          )
        } catch (e: Exception) {
          log.warn("Failed to fetch metric ${query.id}: ${e.message}")
          FormattedMetricValue(
            id = query.id,
            name = query.name,
            description = query.description,
            rawValue = null,
            formattedValue = null,
            unit = query.unit,
            category = query.category,
            sparkline = null,
          )
        }
      }
    }
    val metrics = futures.map { it.get() }

    log.info("Instance metrics fetch completed in {}ms ({} queries, sparklines={})",
      (System.nanoTime() - startTime) / 1_000_000, interpolatedQueries.size, includeSparklines)

    val response = InstanceMetricsResponse(
      metrics = metrics,
      metricsEnabled = true,
      sparklinesEnabled = sparklinesEnabled,
    )

    cache[cacheKey] = CacheEntry(response, System.currentTimeMillis())
    return response
  }

  private fun buildCacheKey(request: InstanceMetricsRequest, includeSparklines: Boolean): String {
    return "${request.clusterName}:${request.clusterId}:${request.namespace}:" +
      "${request.environment}:${request.cell}:${request.workloadName}:" +
      "${request.workloadKind}:${request.team}:${request.version}:$includeSparklines"
  }

  private fun formatValue(result: MetricInstantResult?, unit: MetricUnit): String? {
    val value = result?.value ?: return null

    // Handle NaN and Infinity
    if (value.isNaN() || value.isInfinite()) {
      return null
    }

    return when (unit) {
      MetricUnit.PERCENTAGE -> {
        val percentage = value * 100
        "${formatNumber(percentage, 2)}%"
      }
      MetricUnit.REQUESTS_PER_SECOND -> {
        "${formatNumber(value, 2)} req/s"
      }
      MetricUnit.MILLISECONDS -> {
        // Convert from seconds to milliseconds if value appears to be in seconds
        val ms = if (value < 1) value * 1000 else value
        "${formatNumber(ms, 1)} ms"
      }
      MetricUnit.SECONDS -> {
        "${formatNumber(value, 2)} s"
      }
      MetricUnit.COUNT -> {
        formatNumber(value, 0)
      }
      MetricUnit.BYTES -> {
        formatBytes(value)
      }
    }
  }

  private fun formatNumber(value: Double, decimals: Int): String {
    return if (decimals == 0) {
      value.toLong().toString()
    } else {
      BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).toString()
    }
  }

  private fun formatBytes(bytes: Double): String {
    return when {
      bytes >= 1_073_741_824 -> "${formatNumber(bytes / 1_073_741_824, 2)} GB"
      bytes >= 1_048_576 -> "${formatNumber(bytes / 1_048_576, 2)} MB"
      bytes >= 1024 -> "${formatNumber(bytes / 1024, 2)} KB"
      else -> "${formatNumber(bytes, 0)} B"
    }
  }
}

data class MetricsStatusResponse(
  val metricsEnabled: Boolean,
  val sparklinesEnabled: Boolean,
)
