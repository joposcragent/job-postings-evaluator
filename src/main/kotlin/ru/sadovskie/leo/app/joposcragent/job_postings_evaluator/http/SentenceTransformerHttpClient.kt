package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http

import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Component
class SentenceTransformerHttpClient(
	private val sentenceTransformerRestClient: RestClient,
) {

	private val vectorType = object : ParameterizedTypeReference<List<Double>>() {}

	fun vectorize(text: String): List<Double> =
		try {
			sentenceTransformerRestClient.post()
				.uri("/text/vectorize")
				.contentType(MediaType.APPLICATION_JSON)
				.body(TextVectorizeRequest(text))
				.retrieve()
				.body(vectorType)!!
		} catch (ex: RestClientResponseException) {
			throw ExternalServiceException("sentence-transformer vectorize failed: ${ex.statusCode} ${ex.responseBodyAsString}", ex)
		}

	fun cosineSimilarity(left: List<Double>, right: List<Double>): Double {
		try {
			return sentenceTransformerRestClient.post()
				.uri("/vectors/cosine-similarity")
				.contentType(MediaType.APPLICATION_JSON)
				.body(VectorsPairRequest(left = left, right = right))
				.retrieve()
				.body(CosineSimilarityDto::class.java)!!
				.similarity
		} catch (ex: RestClientResponseException) {
			throw ExternalServiceException(
				"sentence-transformer cosine-similarity failed: ${ex.statusCode} ${ex.responseBodyAsString}",
				ex,
			)
		}
	}
}
