//package sh.apptrail.controlplane.infrastructure.config
//
//import org.slf4j.LoggerFactory
//import org.springframework.boot.context.event.ApplicationReadyEvent
//import org.springframework.context.event.EventListener
//import org.springframework.stereotype.Component
//import org.springframework.transaction.annotation.Transactional
//import sh.apptrail.controlplane.application.service.ClusterTopologyResolver
//import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadInstanceRepository
//
///**
// * Startup task that backfills the cell field for existing workload instances
// * based on the current cell configuration.
// */
//@Component
//class CellBackfillRunner(
//  private val workloadInstanceRepository: WorkloadInstanceRepository,
//  private val clusterTopologyResolver: ClusterTopologyResolver,
//) {
//
//  private val log = LoggerFactory.getLogger(CellBackfillRunner::class.java)
//
//  @EventListener(ApplicationReadyEvent::class)
//  @Transactional
//  fun onApplicationReady() {
//    log.info("Checking for workload instances that need cell backfill...")
//
//    val instances = workloadInstanceRepository.findAll()
//    var updatedCount = 0
//
//    for (instance in instances) {
//      val clusterId = instance.cluster.name
//      val namespace = instance.namespace
//      val cellInfo = clusterTopologyResolver.resolveCell(clusterId, namespace)
//
//      val expectedCell = cellInfo?.name
//
//      // Only update if cell value is different
//      if (instance.cell != expectedCell) {
//        instance.cell = expectedCell
//        workloadInstanceRepository.save(instance)
//        updatedCount++
//
//        if (expectedCell != null) {
//          log.debug(
//            "Backfilled cell '{}' for workload instance: cluster={}, namespace={}, workload={}",
//            expectedCell, clusterId, namespace, instance.workload.name
//          )
//        }
//      }
//    }
//
//    if (updatedCount > 0) {
//      log.info("Backfilled cell for {} workload instance(s)", updatedCount)
//    } else {
//      log.info("No workload instances needed cell backfill")
//    }
//  }
//}
