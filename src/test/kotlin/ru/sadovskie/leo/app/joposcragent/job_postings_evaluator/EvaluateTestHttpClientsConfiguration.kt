package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator

import org.mockito.Mockito
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.SentenceTransformerFeignClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.SettingsHttpClient

@TestConfiguration(proxyBeanMethods = false)
class EvaluateTestHttpClientsConfiguration {

	@Bean
	@Primary
	fun settingsHttpClient(): SettingsHttpClient =
		Mockito.mock(SettingsHttpClient::class.java)

	@Bean
	@Primary
	fun sentenceTransformerFeignClient(): SentenceTransformerFeignClient =
		Mockito.mock(SentenceTransformerFeignClient::class.java)
}
