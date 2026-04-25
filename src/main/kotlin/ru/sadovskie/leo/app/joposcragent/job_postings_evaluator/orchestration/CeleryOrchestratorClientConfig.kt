package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.orchestration

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration
@EnableConfigurationProperties(CeleryOrchestratorProperties::class)
class CeleryOrchestratorClientConfig {

	@Bean
	@Qualifier("celeryOrchestrator")
	fun celeryOrchestratorRestClient(properties: CeleryOrchestratorProperties): RestClient {
		val httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(10))
			.build()
		val requestFactory = BufferingClientHttpRequestFactory(JdkClientHttpRequestFactory(httpClient))
		return RestClient.builder()
			.baseUrl(properties.baseUrl.trimEnd('/'))
			.requestFactory(requestFactory)
			.build()
	}
}
