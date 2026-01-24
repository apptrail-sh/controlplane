package sh.apptrail.controlplane.infrastructure.notification

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
@EnableConfigurationProperties(NotificationProperties::class)
class NotificationConfig {

  @Bean("notificationExecutor")
  fun notificationExecutor(): Executor {
    return ThreadPoolTaskExecutor().apply {
      corePoolSize = 2
      maxPoolSize = 5
      queueCapacity = 100
      setThreadNamePrefix("notification-")
      initialize()
    }
  }
}
