package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class HttpClientsConfiguration(
	private val properties: JoposcragentProperties,
) {

	@Bean
	fun settingsManagerRestClient(): RestClient =
		RestClient.builder().baseUrl(properties.settingsManagerBaseUrl).build()

	@Bean
	fun sentenceTransformerRestClient(): RestClient =
		RestClient.builder().baseUrl(properties.sentenceTransformerBaseUrl).build()
}
