package sh.apptrail.controlplane.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "apptrail.pod-metrics")
data class PodMetricsProperties(
  val enabled: Boolean = false,
  val clusterLabel: String = "cluster",
  val namespaceLabel: String = "namespace",
  val podLabel: String = "pod",
  val cpuQuery: String = "sum(rate(container_cpu_usage_seconds_total{{{podLabel}}=\"{{podName}}\", {{namespaceLabel}}=\"{{namespace}}\", {{clusterLabel}}=\"{{clusterName}}\", container!=\"\"}[5m]))",
  val memoryQuery: String = "sum(container_memory_working_set_bytes{{{podLabel}}=\"{{podName}}\", {{namespaceLabel}}=\"{{namespace}}\", {{clusterLabel}}=\"{{clusterName}}\", container!=\"\"})",
  val cpuLimitQuery: String = "sum(kube_pod_container_resource_limits{resource=\"cpu\", {{podLabel}}=\"{{podName}}\", {{namespaceLabel}}=\"{{namespace}}\", {{clusterLabel}}=\"{{clusterName}}\"})",
  val memoryLimitQuery: String = "sum(kube_pod_container_resource_limits{resource=\"memory\", {{podLabel}}=\"{{podName}}\", {{namespaceLabel}}=\"{{namespace}}\", {{clusterLabel}}=\"{{clusterName}}\"})",
)
