package sh.apptrail.controlplane.application.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import sh.apptrail.controlplane.infrastructure.alerting.prometheus.AlertAggregation
import sh.apptrail.controlplane.infrastructure.alerting.prometheus.AlertInfo
import sh.apptrail.controlplane.infrastructure.alerting.prometheus.PrometheusClient
import sh.apptrail.controlplane.infrastructure.alerting.prometheus.PrometheusProperties
import sh.apptrail.controlplane.infrastructure.alerting.prometheus.WorkloadInstanceKey
import java.util.concurrent.ConcurrentHashMap

@Service
class AlertService(
  private val prometheusClient: PrometheusClient?,
  private val prometheusProperties: PrometheusProperties?,
) {
  private val log = LoggerFactory.getLogger(AlertService::class.java)

  private data class CacheEntry(
    val alerts: List<AlertInfo>,
    val timestamp: Long,
  )

  private data class RecentAlertCountsCacheEntry(
    val counts: Map<WorkloadInstanceKey, Int>,
    val timestamp: Long,
  )

  private val alertsCache = ConcurrentHashMap<String, CacheEntry>()
  private var allAlertsCache: CacheEntry? = null
  private var recentAlertCountsCache: RecentAlertCountsCacheEntry? = null

  fun isEnabled(): Boolean = prometheusClient != null && prometheusProperties?.enabled == true

  fun getAllFiringAlerts(): List<AlertInfo>? {
    if (!isEnabled()) {
      return null
    }

    val cacheTtlMs = (prometheusProperties?.cache?.ttlSeconds ?: 30) * 1000

    val cached = allAlertsCache
    if (cached != null && prometheusProperties?.cache?.enabled == true) {
      if (System.currentTimeMillis() - cached.timestamp < cacheTtlMs) {
        return cached.alerts
      }
    }

    val alerts = prometheusClient?.queryFiringAlerts() ?: emptyList()
    allAlertsCache = CacheEntry(alerts, System.currentTimeMillis())
    return alerts
  }

  fun getAlertsForInstance(
    workloadName: String,
    workloadKind: String,
    clusterName: String,
    namespace: String,
  ): AlertsResult? {
    if (!isEnabled()) {
      return null
    }

    val cacheKey = "$workloadName:$workloadKind:$clusterName:$namespace"
    val cacheTtlMs = (prometheusProperties?.cache?.ttlSeconds ?: 30) * 1000

    val cached = alertsCache[cacheKey]
    val recentAlertCounts = getRecentAlertCounts()
    val recentCount = recentAlertCounts[WorkloadInstanceKey(
      workload = workloadName,
      workloadType = workloadKind.lowercase(),
      cluster = clusterName,
      namespace = namespace,
    )]

    if (cached != null && prometheusProperties?.cache?.enabled == true) {
      if (System.currentTimeMillis() - cached.timestamp < cacheTtlMs) {
        return buildAlertsResult(cached.alerts, recentCount)
      }
    }

    val alerts = prometheusClient?.queryAlertsForWorkload(
      workload = workloadName,
      workloadType = workloadKind.lowercase(),
      cluster = clusterName,
      namespace = namespace,
    ) ?: emptyList()

    alertsCache[cacheKey] = CacheEntry(alerts, System.currentTimeMillis())
    return buildAlertsResult(alerts, recentCount)
  }

  fun getAlertsForInstances(
    instances: List<InstanceKey>,
  ): Map<InstanceKey, AlertsResult> {
    if (!isEnabled()) {
      return emptyMap()
    }

    val allAlerts = getAllFiringAlerts() ?: return emptyMap()
    val recentAlertCounts = getRecentAlertCounts()

    return instances.associateWith { instance ->
      val matchingAlerts = allAlerts.filter { alert ->
        alert.workload == instance.workloadName &&
          alert.workloadType == instance.workloadKind.lowercase() &&
          alert.cluster == instance.clusterName &&
          alert.namespace == instance.namespace
      }
      val recentCount = recentAlertCounts[WorkloadInstanceKey(
        workload = instance.workloadName,
        workloadType = instance.workloadKind.lowercase(),
        cluster = instance.clusterName,
        namespace = instance.namespace,
      )]
      buildAlertsResult(matchingAlerts, recentCount)
    }
  }

  private fun getRecentAlertCounts(): Map<WorkloadInstanceKey, Int> {
    val cacheTtlMs = (prometheusProperties?.cache?.ttlSeconds ?: 30) * 1000

    val cached = recentAlertCountsCache
    if (cached != null && prometheusProperties?.cache?.enabled == true) {
      if (System.currentTimeMillis() - cached.timestamp < cacheTtlMs) {
        return cached.counts
      }
    }

    val counts = prometheusClient?.queryRecentAlertCountsForWorkloads() ?: emptyMap()
    recentAlertCountsCache = RecentAlertCountsCacheEntry(counts, System.currentTimeMillis())
    return counts
  }

  fun getAlertAggregations(): AlertAggregation? {
    if (!isEnabled()) {
      return null
    }

    val bySeverity = prometheusClient?.queryAlertCountsBySeverity() ?: emptyMap()
    val byCluster = prometheusClient?.queryAlertCountsByCluster() ?: emptyMap()
    val byName = prometheusClient?.queryAlertCountsByName() ?: emptyMap()

    return AlertAggregation(
      bySeverity = bySeverity,
      byCluster = byCluster,
      byAlertName = byName,
      totalCount = bySeverity.values.sum(),
      criticalCount = bySeverity["critical"] ?: 0,
      warningCount = bySeverity["warning"] ?: 0,
    )
  }

  fun getCriticalAlerts(): List<AlertInfo>? {
    if (!isEnabled()) {
      return null
    }
    return prometheusClient?.queryCriticalAlerts()
  }

  private fun buildAlertsResult(alerts: List<AlertInfo>, recentAlertCount: Int? = null): AlertsResult {
    return AlertsResult(
      count = alerts.size,
      hasCritical = alerts.any { it.severity?.lowercase() == "critical" },
      hasWarning = alerts.any { it.severity?.lowercase() == "warning" },
      details = alerts.map { alert ->
        AlertDetail(
          name = alert.alertName,
          severity = alert.severity,
          activeForSeconds = alert.activeForSeconds,
          alertGroup = alert.alertGroup,
          service = alert.service,
        )
      },
      recentAlertCount = recentAlertCount,
    )
  }
}

data class InstanceKey(
  val workloadName: String,
  val workloadKind: String,
  val clusterName: String,
  val namespace: String,
)

data class AlertsResult(
  val count: Int,
  val hasCritical: Boolean,
  val hasWarning: Boolean,
  val details: List<AlertDetail>,
  val recentAlertCount: Int?,
)

data class AlertDetail(
  val name: String,
  val severity: String?,
  val activeForSeconds: Long?,
  val alertGroup: String?,
  val service: String?,
)
