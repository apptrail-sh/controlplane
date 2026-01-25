package sh.apptrail.controlplane.application.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadInstanceRepository

@Service
class CellBackfillService(
  private val workloadInstanceRepository: WorkloadInstanceRepository,
  private val clusterTopologyResolver: ClusterTopologyResolver,
) {

  private val log = LoggerFactory.getLogger(CellBackfillService::class.java)

  @Transactional
  fun runBackfill(): BackfillResult {
    log.info("Starting cell backfill...")

    val instances = workloadInstanceRepository.findAll()
    var updatedCount = 0

    for (instance in instances) {
      val clusterId = instance.cluster.name
      val namespace = instance.namespace
      val cellInfo = clusterTopologyResolver.resolveCell(clusterId, namespace)
      val expectedCell = cellInfo?.name

      if (instance.cell != expectedCell) {
        instance.cell = expectedCell
        workloadInstanceRepository.save(instance)
        updatedCount++

        if (expectedCell != null) {
          log.debug(
            "Backfilled cell '{}' for workload instance: cluster={}, namespace={}, workload={}",
            expectedCell, clusterId, namespace, instance.workload.name
          )
        }
      }
    }

    log.info("Cell backfill completed. Processed {} instances, updated {} instances.", instances.size, updatedCount)
    return BackfillResult(totalProcessed = instances.size, updatedCount = updatedCount)
  }
}

data class BackfillResult(
  val totalProcessed: Int,
  val updatedCount: Int,
)
