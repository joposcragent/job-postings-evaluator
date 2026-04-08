package ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.config.SentenceTransformerProperties

@Component
class SentenceTransformerClient(
	builder: RestClient.Builder,
	props: SentenceTransformerProperties,
) {

	private val client: RestClient = builder.baseUrl(props.baseUrl).build()

	fun cosineSimilarity(left: List<Double>, right: List<Double>): Double {
		val response = client.post()
			.uri("/vectors/cosine-similarity")
			.contentType(MediaType.APPLICATION_JSON)
			.body(VectorsPair(left, right))
			.retrieve()
			.body(CosineSimilarityResponse::class.java)
			?: error("Empty body from POST /vectors/cosine-similarity")
		return response.similarity
	}

	private data class VectorsPair(
		val left: List<Double>,
		val right: List<Double>,
	)
}
