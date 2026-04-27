package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties("joposcragent", ignoreUnknownFields = true)
data class JoposcragentDownstreamProperties(
	@field:NestedConfigurationProperty
	val jobPostingsCrud: ServiceUrl = ServiceUrl(),
	@field:NestedConfigurationProperty
	val settingsManager: ServiceUrl = ServiceUrl(),
	@field:NestedConfigurationProperty
	val sentenceTransformer: ServiceUrl = ServiceUrl(),
)

data class ServiceUrl(
	val baseUrl: String = "http://localhost:8080",
)

@ConfigurationProperties("celery.orchestrator", ignoreUnknownFields = false)
data class CeleryOrchestratorProperties(
	val baseUrl: String = "http://celery-orchestrator-api:8000",
)
