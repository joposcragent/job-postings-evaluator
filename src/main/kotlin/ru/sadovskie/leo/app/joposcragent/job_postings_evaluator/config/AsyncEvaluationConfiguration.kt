package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.config

import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncEvaluationConfiguration : AsyncConfigurer {

	override fun getAsyncExecutor(): Executor {
		val ex = ThreadPoolTaskExecutor()
		ex.setThreadNamePrefix("evaluate-async-")
		ex.corePoolSize = 2
		ex.maxPoolSize = 4
		ex.initialize()
		return ex
	}

	override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler =
		AsyncUncaughtExceptionHandler { ex, method, _ ->
			LoggerFactory.getLogger(method.declaringClass).error("Async evaluation failed in {}", method.name, ex)
		}
}
