package sh.apptrail.controlplane.web.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.application.service.ClusterEnvironmentResolver
import sh.apptrail.controlplane.infrastructure.config.ClusterEnvironmentProperties

@RestController
@RequestMapping("/api/v1/environments")
class EnvironmentController(
  private val properties: ClusterEnvironmentProperties,
  private val clusterEnvironmentResolver: ClusterEnvironmentResolver,
) {
  @GetMapping
  fun getEnvironments(): EnvironmentsResponse {
    val shardsByEnv = clusterEnvironmentResolver.getShardsByEnvironment()

    return EnvironmentsResponse(
      environments = properties.environmentOrder.mapIndexed { index, name ->
        val envShards = shardsByEnv[name]?.map { ShardInfoResponse(name = it.name, order = it.order) }
        EnvironmentInfo(name = name, order = index, shards = envShards)
      }
    )
  }
}

data class EnvironmentsResponse(val environments: List<EnvironmentInfo>)
data class EnvironmentInfo(
  val name: String,
  val order: Int,
  val shards: List<ShardInfoResponse>? = null,
)
data class ShardInfoResponse(val name: String, val order: Int)
