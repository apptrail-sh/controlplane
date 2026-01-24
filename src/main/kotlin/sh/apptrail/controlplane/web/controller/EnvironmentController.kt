package sh.apptrail.controlplane.web.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.application.service.ClusterTopologyResolver

@RestController
@RequestMapping("/api/v1/environments")
class EnvironmentController(
  private val clusterTopologyResolver: ClusterTopologyResolver,
) {
  @GetMapping
  fun getEnvironments(): EnvironmentsResponse {
    val shardsByEnv = clusterTopologyResolver.getShardsByEnvironment()
    val environments = clusterTopologyResolver.getEnvironments()

    return EnvironmentsResponse(
      environments = environments.map { env ->
        val envShards = shardsByEnv[env.name]?.map { ShardInfoResponse(name = it.name, order = it.order) }
        EnvironmentInfo(name = env.name, order = env.order, shards = envShards)
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
