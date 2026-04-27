package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableAsync
class AsyncConfig {

	@Bean(name = ["evaluationExecutor"])
	fun evaluationExecutor(): TaskExecutor {
		val ex = ThreadPoolTaskExecutor()
		ex.setThreadNamePrefix("eval-")
		ex.corePoolSize = 4
		ex.maxPoolSize = 8
		ex.setQueueCapacity(200)
		ex.setWaitForTasksToCompleteOnShutdown(true)
		ex.setAwaitTerminationSeconds(30)
		ex.initialize()
		return ex
	}
}
