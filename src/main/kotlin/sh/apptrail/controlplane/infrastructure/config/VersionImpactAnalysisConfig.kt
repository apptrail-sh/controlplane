package sh.apptrail.controlplane.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@ConfigurationProperties(prefix = "app.version-impact-analysis")
data class VersionImpactAnalysisProperties(
  val enabled: Boolean = false,
  val delayMinutes: Long = 15,
  val windowMinutes: Long = 15,
  val pollIntervalSeconds: Long = 60,
  val maxConcurrentAnalyses: Int = 5,
  val metrics: MetricsConfig = MetricsConfig()
)

data class MetricsConfig(
  val cpu: MetricConfig = MetricConfig(
    enabled = true,
    thresholdPercent = 20.0,
    query = """
      sum(node_namespace_pod_container:container_cpu_usage_seconds_total:sum_irate{
        cluster="{{cluster}}", namespace="{{namespace}}"
      } * on(namespace,pod) group_left(workload, workload_type)
      namespace_workload_pod:kube_pod_owner:relabel{
        cluster="{{cluster}}", namespace="{{namespace}}",
        workload_type="{{workloadType}}", workload="{{workload}}"
      }) /
      sum(kube_pod_container_resource_requests{
        cluster="{{cluster}}", namespace="{{namespace}}",
        resource="cpu"
      } * on(namespace,pod) group_left(workload, workload_type)
      namespace_workload_pod:kube_pod_owner:relabel{
        cluster="{{cluster}}", namespace="{{namespace}}",
        workload_type="{{workloadType}}", workload="{{workload}}"
      })
    """.trimIndent().replace("\n", " ")
  ),
  val memory: MetricConfig = MetricConfig(
    enabled = true,
    thresholdPercent = 20.0,
    query = """
      sum(container_memory_working_set_bytes{
        cluster="{{cluster}}", namespace="{{namespace}}", container!=""
      } * on(namespace,pod) group_left(workload, workload_type)
      namespace_workload_pod:kube_pod_owner:relabel{
        cluster="{{cluster}}", namespace="{{namespace}}",
        workload_type="{{workloadType}}", workload="{{workload}}"
      }) /
      sum(kube_pod_container_resource_requests{
        cluster="{{cluster}}", namespace="{{namespace}}",
        resource="memory"
      } * on(namespace,pod) group_left(workload, workload_type)
      namespace_workload_pod:kube_pod_owner:relabel{
        cluster="{{cluster}}", namespace="{{namespace}}",
        workload_type="{{workloadType}}", workload="{{workload}}"
      })
    """.trimIndent().replace("\n", " ")
  ),
  val restarts: MetricConfig = MetricConfig(
    enabled = true,
    thresholdPercent = 0.0,
    thresholdAbsolute = 1.0,
    query = """
      sum(increase(kube_pod_container_status_restarts_total{
        cluster="{{cluster}}", namespace="{{namespace}}"
      }[{{window}}]) * on(namespace,pod) group_left(workload, workload_type)
      namespace_workload_pod:kube_pod_owner:relabel{
        cluster="{{cluster}}", namespace="{{namespace}}",
        workload_type="{{workloadType}}", workload="{{workload}}"
      })
    """.trimIndent().replace("\n", " ")
  ),
  val errorRate: MetricConfig = MetricConfig(
    enabled = false,
    thresholdPercent = 50.0,
    query = ""
  ),
  val latencyP99: MetricConfig = MetricConfig(
    enabled = false,
    thresholdPercent = 25.0,
    query = ""
  )
)

data class MetricConfig(
  val enabled: Boolean = false,
  val thresholdPercent: Double = 20.0,
  val thresholdAbsolute: Double? = null,
  val query: String = ""
)

@Configuration
@EnableConfigurationProperties(VersionImpactAnalysisProperties::class)
@EnableScheduling
class VersionImpactAnalysisConfig
