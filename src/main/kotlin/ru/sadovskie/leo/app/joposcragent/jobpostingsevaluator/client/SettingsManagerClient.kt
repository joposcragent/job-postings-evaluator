package ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.config.SettingsManagerProperties

@Component
class SettingsManagerClient(
	builder: RestClient.Builder,
	props: SettingsManagerProperties,
) {

	private val client: RestClient = builder.baseUrl(props.baseUrl).build()

	fun relevanceThreshold(type: String): Double {
		val item = client.get()
			.uri("/relevance-thresholds/{type}", type)
			.accept(MediaType.APPLICATION_JSON)
			.retrieve()
			.body(RelevanceThresholdItem::class.java)
			?: error("Empty body from GET /relevance-thresholds/$type")
		return item.value
	}

	fun getReferenceVector(): List<Double> {
		val entity = client.get()
			.uri("/reference-context")
			.accept(MediaType.APPLICATION_JSON)
			.retrieve()
			.toEntity(ReferenceContextBody::class.java)
		if (entity.statusCode.value() == 202) {
			error("Reference context is not set (HTTP 202 from GET /reference-context)")
		}
		val vector = entity.body?.vector
		if (vector.isNullOrEmpty()) {
			error("Reference vector is missing or empty")
		}
		return vector
	}
}
