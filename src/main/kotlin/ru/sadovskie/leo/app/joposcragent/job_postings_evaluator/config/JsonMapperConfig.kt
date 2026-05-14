package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.config

import tools.jackson.module.kotlin.kotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

@Configuration
class JsonMapperConfig {

	@Bean
	fun jsonMapper(): JsonMapper = JsonMapper.builder()
		.addModule(kotlinModule())
		.build()
}
