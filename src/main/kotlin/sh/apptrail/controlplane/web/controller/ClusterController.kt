package sh.apptrail.controlplane.web.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sh.apptrail.controlplane.application.service.ClusterTopologyResolver
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterRepository
import java.time.Instant

@RestController
@RequestMapping("/api/v1/clusters")
class ClusterController(
  private val clusterRepository: ClusterRepository,
  private val clusterTopologyResolver: ClusterTopologyResolver,
) {
  @GetMapping
  fun listClusters(): List<ClusterDetailResponse> {
    return clusterRepository.findAll().map { cluster ->
      ClusterDetailResponse(
        id = cluster.id ?: 0,
        name = cluster.name,
        alias = clusterTopologyResolver.resolveAlias(cluster.name),
        createdAt = cluster.createdAt,
        updatedAt = cluster.updatedAt,
      )
    }
  }

  @GetMapping("/{id}")
  fun getCluster(@PathVariable id: Long): ResponseEntity<ClusterDetailResponse> {
    val cluster = clusterRepository.findById(id).orElse(null)
      ?: return ResponseEntity.notFound().build()

    return ResponseEntity.ok(
      ClusterDetailResponse(
        id = cluster.id ?: 0,
        name = cluster.name,
        alias = clusterTopologyResolver.resolveAlias(cluster.name),
        createdAt = cluster.createdAt,
        updatedAt = cluster.updatedAt,
      )
    )
  }
}

data class ClusterDetailResponse(
  val id: Long,
  val name: String,
  val alias: String?,
  val createdAt: Instant?,
  val updatedAt: Instant?,
)
