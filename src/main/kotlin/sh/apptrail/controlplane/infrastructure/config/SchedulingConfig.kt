package sh.apptrail.controlplane.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar

@Configuration
class SchedulingConfig : SchedulingConfigurer {

  override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
    taskRegistrar.setScheduler(taskScheduler())
  }

  @Bean(name = ["taskScheduler"])
  fun taskScheduler(): TaskScheduler {
    return ThreadPoolTaskScheduler().apply {
      poolSize = 2
      setThreadNamePrefix("app-scheduler-")
      initialize()
    }
  }
}
