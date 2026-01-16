package sh.apptrail.controlplane.web.dto

import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.URL

data class UpdateWorkloadRequest(
  @field:URL(message = "Invalid URL format")
  @field:Size(max = 2048, message = "Repository URL must be at most 2048 characters")
  val repositoryUrl: String? = null,

  @field:Size(max = 2000, message = "Description must be at most 2000 characters")
  val description: String? = null,
)
