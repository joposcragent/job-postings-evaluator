package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http

import feign.RequestInterceptor
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
	name = "sentence-transformer",
	url = "\${joposcragent.sentence-transformer-base-url}",
	configuration = [SentenceTransformerFeignClient.Config::class],
	primary = false,
)
interface SentenceTransformerFeignClient {

	@PostMapping(
		path = ["/text/vectorize"],
		consumes = [MediaType.APPLICATION_JSON_VALUE],
		produces = [MediaType.APPLICATION_JSON_VALUE],
	)
	fun vectorize(@RequestBody body: TextVectorizeRequest): List<Double>

	@PostMapping(
		path = ["/vectors/cosine-similarity"],
		consumes = [MediaType.APPLICATION_JSON_VALUE],
		produces = [MediaType.APPLICATION_JSON_VALUE],
	)
	fun cosineSimilarity(@RequestBody body: VectorsPairRequest): CosineSimilarityDto

	class Config {

		@Bean
		fun sentenceTransformerRequestInterceptor(): RequestInterceptor =
			RequestInterceptor { template ->
				template.header(HttpHeaders.CONNECTION, "keep-alive")
			}
	}
}
