package sh.apptrail.controlplane

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import sh.apptrail.controlplane.infrastructure.alerting.prometheus.PrometheusProperties
import sh.apptrail.controlplane.infrastructure.gitprovider.github.GitHubProperties

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(GitHubProperties::class, PrometheusProperties::class)
class ControlplaneApplication

fun main(args: Array<String>) {
  runApplication<ControlplaneApplication>(*args)
}
