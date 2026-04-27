package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.config

import feign.Client
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

/**
 * Force HTTP/1.1 to peers that mishandle h2c (e.g. some FastAPI/uvicorn setups on plain HTTP).
 * Spring Cloud OpenFeign uses this [Client] for Feign if present.
 */
@Configuration
class OpenFeignOkConfig {

	@Bean
	fun okHttp3FeignClient(): Client {
		val client = OkHttpClient.Builder()
			.protocols(listOf(Protocol.HTTP_1_1))
			.connectTimeout(10, TimeUnit.SECONDS)
			.readTimeout(120, TimeUnit.SECONDS)
			.build()
		return feign.okhttp.OkHttpClient(client)
	}
}
