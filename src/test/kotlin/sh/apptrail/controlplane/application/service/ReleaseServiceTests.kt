package sh.apptrail.controlplane.application.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import sh.apptrail.controlplane.infrastructure.persistence.entity.ReleaseEntity
import sh.apptrail.controlplane.infrastructure.persistence.entity.RepositoryEntity
import sh.apptrail.controlplane.infrastructure.persistence.repository.ReleaseRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.VersionHistoryRepository
import sh.apptrail.controlplane.infrastructure.persistence.repository.WorkloadRepository
import java.time.Instant

class ReleaseServiceTests {

  private lateinit var releaseRepository: ReleaseRepository
  private lateinit var versionHistoryRepository: VersionHistoryRepository
  private lateinit var workloadRepository: WorkloadRepository
  private lateinit var releaseService: ReleaseService
  private lateinit var testRepository: RepositoryEntity

  @BeforeEach
  fun setUp() {
    releaseRepository = mock()
    versionHistoryRepository = mock()
    workloadRepository = mock()
    releaseService = ReleaseService(releaseRepository, versionHistoryRepository, workloadRepository)
    testRepository = RepositoryEntity(id = 1L, url = "https://github.com/org/repo", provider = "github")
  }

  private fun createRelease(tagName: String, name: String? = null): ReleaseEntity {
    return ReleaseEntity(
      id = 1L,
      repository = testRepository,
      tagName = tagName,
      name = name,
      provider = "github",
      fetchedAt = Instant.now()
    )
  }

  @Nested
  inner class FindReleaseWithVersionNormalizationTests {

    @Test
    fun `matches exact version with v prefix`() {
      val release = createRelease(tagName = "v2.445.11", name = "Release v2.445.11")
      whenever(releaseRepository.findByRepositoryAndTagName(testRepository, "v2.445.11"))
        .thenReturn(release)

      val result = releaseService.findReleaseWithVersionNormalization(testRepository, "v2.445.11")

      assertNotNull(result)
      assertEquals("v2.445.11", result!!.tagName)
    }

    @Test
    fun `matches version without v prefix by adding v prefix`() {
      val release = createRelease(tagName = "v2.445.11", name = "Release v2.445.11")
      whenever(releaseRepository.findByRepositoryAndTagName(testRepository, "2.445.11"))
        .thenReturn(null)
      whenever(releaseRepository.findByRepositoryAndTagName(testRepository, "v2.445.11"))
        .thenReturn(release)

      val result = releaseService.findReleaseWithVersionNormalization(testRepository, "2.445.11")

      assertNotNull(result)
      assertEquals("v2.445.11", result!!.tagName)
    }

    @Test
    fun `matches version with v prefix by removing v prefix`() {
      val release = createRelease(tagName = "2.445.11", name = "Release 2.445.11")
      whenever(releaseRepository.findByRepositoryAndTagName(testRepository, "v2.445.11"))
        .thenReturn(null)
      whenever(releaseRepository.findByRepositoryAndTagName(testRepository, "vv2.445.11"))
        .thenReturn(null)
      whenever(releaseRepository.findByRepositoryAndTagName(testRepository, "2.445.11"))
        .thenReturn(release)

      val result = releaseService.findReleaseWithVersionNormalization(testRepository, "v2.445.11")

      assertNotNull(result)
      assertEquals("2.445.11", result!!.tagName)
    }

    @Test
    fun `returns null when no matching release exists`() {
      whenever(releaseRepository.findByRepositoryAndTagName(eq(testRepository), any()))
        .thenReturn(null)

      val result = releaseService.findReleaseWithVersionNormalization(testRepository, "1.0.0")

      assertNull(result)
    }

    @Test
    fun `release title does not affect matching - only tag name matters`() {
      // Release has title "Release v2.445.11" but tag is "v2.445.11"
      // Matching uses tag, not title
      val release = createRelease(tagName = "v2.445.11", name = "Release v2.445.11")
      whenever(releaseRepository.findByRepositoryAndTagName(testRepository, "v2.445.11"))
        .thenReturn(release)

      val result = releaseService.findReleaseWithVersionNormalization(testRepository, "v2.445.11")

      assertNotNull(result)
      assertEquals("Release v2.445.11", result!!.name)
      assertEquals("v2.445.11", result.tagName)
    }

    @Test
    fun `handles semantic versions correctly`() {
      val release = createRelease(tagName = "v1.2.3", name = "v1.2.3")
      whenever(releaseRepository.findByRepositoryAndTagName(testRepository, "1.2.3"))
        .thenReturn(null)
      whenever(releaseRepository.findByRepositoryAndTagName(testRepository, "v1.2.3"))
        .thenReturn(release)

      val result = releaseService.findReleaseWithVersionNormalization(testRepository, "1.2.3")

      assertNotNull(result)
      assertEquals("v1.2.3", result!!.tagName)
    }
  }
}
