package sh.apptrail.controlplane.web.controller

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sh.apptrail.controlplane.application.service.ReleaseFetchAttemptService
import sh.apptrail.controlplane.application.service.ReleaseService
import sh.apptrail.controlplane.application.service.RepositoryService
import sh.apptrail.controlplane.infrastructure.gitprovider.github.GitHubWebhookService

@RestController
@RequestMapping("/webhooks/github")
@ConditionalOnProperty(prefix = "app.gitprovider.github", name = ["enabled"], havingValue = "true")
class GitHubWebhookController(
  private val webhookService: GitHubWebhookService,
  private val releaseService: ReleaseService,
  private val repositoryService: RepositoryService,
  private val releaseFetchAttemptService: ReleaseFetchAttemptService,
) {
  private val log = LoggerFactory.getLogger(GitHubWebhookController::class.java)

  companion object {
    private const val HEADER_SIGNATURE = "X-Hub-Signature-256"
    private const val HEADER_EVENT = "X-GitHub-Event"
    private const val EVENT_RELEASE = "release"
    private const val EVENT_PING = "ping"
  }

  @PostMapping
  fun handleWebhook(
    @RequestHeader(HEADER_SIGNATURE, required = false) signature: String?,
    @RequestHeader(HEADER_EVENT, required = false) event: String?,
    @RequestBody payload: ByteArray,
  ): ResponseEntity<WebhookResponse> {
    // Handle ping events (sent when webhook is first configured)
    if (event == EVENT_PING) {
      log.info("Received GitHub webhook ping event")
      return ResponseEntity.ok(WebhookResponse(status = "ok", message = "pong"))
    }

    // Validate signature
    if (!webhookService.validateSignature(payload, signature)) {
      log.warn("GitHub webhook signature validation failed")
      return ResponseEntity.status(401).body(WebhookResponse(status = "error", message = "Invalid signature"))
    }

    // Only process release events
    if (event != EVENT_RELEASE) {
      log.debug("Ignoring GitHub webhook event type: {}", event)
      return ResponseEntity.ok(WebhookResponse(status = "ignored", message = "Event type not processed"))
    }

    val payloadString = String(payload, Charsets.UTF_8)
    val releasePayload = webhookService.parseReleasePayload(payloadString)

    if (releasePayload == null) {
      log.debug("Release webhook payload not relevant or could not be parsed")
      return ResponseEntity.ok(WebhookResponse(status = "ignored", message = "Release event not processed"))
    }

    log.info(
      "Processing GitHub release webhook: {} for {}",
      releasePayload.releaseInfo.tagName,
      releasePayload.repositoryUrl,
    )

    val repository = repositoryService.findOrCreate(releasePayload.repositoryUrl)

    // Upsert the release
    val release = releaseService.upsertRelease(repository, releasePayload.releaseInfo)

    // Link any pending version history entries
    releaseService.linkPendingVersionHistories(repository, release)

    // Clear any failed fetch attempts for this version (webhook provides definitive release info)
    releaseFetchAttemptService.clearAttempt(repository, releasePayload.releaseInfo.tagName)

    return ResponseEntity.ok(
      WebhookResponse(
        status = "ok",
        message = "Release processed: ${releasePayload.releaseInfo.tagName}",
      )
    )
  }
}

data class WebhookResponse(
  val status: String,
  val message: String,
)
