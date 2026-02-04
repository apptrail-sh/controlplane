package sh.apptrail.controlplane.application.service.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.containers.PostgreSQLContainer
import sh.apptrail.controlplane.application.model.agent.*
import sh.apptrail.controlplane.infrastructure.persistence.repository.ClusterRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionHistoryRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadInstanceRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadRepository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@Testcontainers
class AgentEventProcessorServiceIntegrationTests {

  companion object {
    @Container
    @ServiceConnection
    @JvmStatic
    val postgres = PostgreSQLContainer("postgres:18-alpine")
  }

  @Autowired
  private lateinit var agentEventProcessorService: AgentEventProcessorService

  @Autowired
  private lateinit var versionHistoryRepository: VersionHistoryRepository

  @Autowired
  private lateinit var workloadInstanceRepository: WorkloadInstanceRepository

  @Autowired
  private lateinit var workloadRepository: WorkloadRepository

  @Autowired
  private lateinit var clusterRepository: ClusterRepository

  @BeforeEach
  fun setUp() {
    // Clean up in correct order due to foreign key constraints
    versionHistoryRepository.deleteAll()
    workloadInstanceRepository.deleteAll()
    workloadRepository.deleteAll()
    clusterRepository.deleteAll()
  }

  private fun createAgentEvent(
    workloadName: String = "test-service",
    currentVersion: String = "v1.0.0",
    previousVersion: String? = null,
    phase: DeploymentPhase = DeploymentPhase.PROGRESSING,
    clusterId: String = "test-cluster",
    namespace: String = "default",
  ) = AgentEvent(
    eventId = UUID.randomUUID().toString(),
    occurredAt = Instant.now(),
    environment = "test",
    source = SourceMetadata(
      clusterId = clusterId,
      agentVersion = "1.0.0",
    ),
    workload = WorkloadRef(
      kind = WorkloadKind.DEPLOYMENT,
      name = workloadName,
      namespace = namespace,
    ),
    labels = emptyMap(),
    kind = AgentEventKind.DEPLOYMENT,
    outcome = null,
    revision = Revision(
      current = currentVersion,
      previous = previousVersion,
    ),
    phase = phase,
    error = null,
  )

  @Nested
  inner class `Pre-check finds existing record` {

    @Test
    fun `should update existing version history when duplicate event is processed`() {
      // Given: First event creates the version history
      val event = createAgentEvent(
        currentVersion = "v1.0.0",
        previousVersion = null,
        phase = DeploymentPhase.PROGRESSING,
      )
      agentEventProcessorService.processEvent(event)

      val initialRecords = versionHistoryRepository.findAll()
      assertThat(initialRecords).hasSize(1)
      val initialRecord = initialRecords.first()

      // When: Process duplicate event with updated phase
      val duplicateEvent = createAgentEvent(
        currentVersion = "v1.0.0",
        previousVersion = null,
        phase = DeploymentPhase.COMPLETED,
      )
      agentEventProcessorService.processEvent(duplicateEvent)

      // Then: Should still have exactly one record, updated with new phase
      val finalRecords = versionHistoryRepository.findAll()
      assertThat(finalRecords).hasSize(1)
      assertThat(finalRecords.first().id).isEqualTo(initialRecord.id)
      assertThat(finalRecords.first().deploymentPhase).isEqualTo("completed")
    }
  }

  @Nested
  inner class `Concurrent event processing` {

    @Test
    fun `should handle concurrent identical events without transaction abort errors`() {
      // Given: Prepare for concurrent execution
      val threadCount = 5
      val executor = Executors.newFixedThreadPool(threadCount)
      val startLatch = CountDownLatch(1)
      val doneLatch = CountDownLatch(threadCount)
      val successCount = AtomicInteger(0)
      val errorCount = AtomicInteger(0)
      val errors = mutableListOf<Throwable>()

      // Create identical events for all threads
      val baseEvent = createAgentEvent(
        workloadName = "concurrent-test-service",
        currentVersion = "v2.0.0",
        previousVersion = "v1.9.0",
        phase = DeploymentPhase.PROGRESSING,
      )

      // When: Execute all threads simultaneously
      repeat(threadCount) {
        executor.submit {
          try {
            startLatch.await() // Wait for all threads to be ready
            val event = baseEvent.copy(eventId = UUID.randomUUID().toString())
            agentEventProcessorService.processEvent(event)
            successCount.incrementAndGet()
          } catch (e: Throwable) {
            synchronized(errors) {
              errors.add(e)
            }
            errorCount.incrementAndGet()
          } finally {
            doneLatch.countDown()
          }
        }
      }

      // Release all threads at once
      startLatch.countDown()

      // Wait for completion
      val completed = doneLatch.await(30, TimeUnit.SECONDS)
      executor.shutdown()

      // Then: Verify results
      assertThat(completed).isTrue()

      // Print errors for debugging if any
      if (errors.isNotEmpty()) {
        errors.forEach { e ->
          println("Error: ${e.javaClass.simpleName}: ${e.message}")
        }
      }

      // All threads should succeed (either create or update via pre-check)
      // Some may fail due to race condition, but they should NOT fail with
      // "current transaction is aborted" - they should fail with constraint violation
      // and can be retried
      assertThat(successCount.get() + errorCount.get()).isEqualTo(threadCount)

      // Verify no "transaction is aborted" errors
      errors.forEach { error ->
        assertThat(error.message ?: "")
          .describedAs("Should not have transaction abort error")
          .doesNotContain("current transaction is aborted")
      }

      // Exactly one version history record should exist
      val records = versionHistoryRepository.findAll()
      assertThat(records).hasSize(1)
      assertThat(records.first().currentVersion).isEqualTo("v2.0.0")
      assertThat(records.first().previousVersion).isEqualTo("v1.9.0")
    }

    @Test
    fun `should create single record when processing multiple sequential duplicate events`() {
      // Given: Multiple identical events
      val events = (1..10).map {
        createAgentEvent(
          workloadName = "sequential-test-service",
          currentVersion = "v3.0.0",
          previousVersion = "v2.0.0",
          phase = DeploymentPhase.PROGRESSING,
        )
      }

      // When: Process all events sequentially
      events.forEach { event ->
        agentEventProcessorService.processEvent(event)
      }

      // Then: Exactly one version history record should exist
      val records = versionHistoryRepository.findAll()
      assertThat(records).hasSize(1)
      assertThat(records.first().currentVersion).isEqualTo("v3.0.0")
    }
  }
}
