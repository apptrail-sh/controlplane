package sh.apptrail.controlplane.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "apptrail.node-metrics")
data class NodeMetricsProperties(
  val enabled: Boolean = false,
  val clusterLabel: String = "cluster",
  val nodeLabel: String = "node",
  val cpuQuery: String = "100 - (avg(rate(node_cpu_seconds_total{mode=\"idle\", {{nodeLabel}}=\"{{nodeName}}\", {{clusterLabel}}=\"{{clusterName}}\"}[5m])) * 100)",
  val memoryQuery: String = "(1 - (node_memory_MemAvailable_bytes{{{nodeLabel}}=\"{{nodeName}}\", {{clusterLabel}}=\"{{clusterName}}\"} / node_memory_MemTotal_bytes{{{nodeLabel}}=\"{{nodeName}}\", {{clusterLabel}}=\"{{clusterName}}\"})) * 100",
)
