package sh.apptrail.controlplane.infrastructure.ai

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import sh.apptrail.controlplane.infrastructure.ai.openai.AIProperties

@Configuration
@EnableConfigurationProperties(AIProperties::class)
class AIPropertiesConfig
