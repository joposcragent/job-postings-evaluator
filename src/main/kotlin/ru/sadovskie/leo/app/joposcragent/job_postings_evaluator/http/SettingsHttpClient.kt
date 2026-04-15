package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Component
class SettingsHttpClient(
	private val settingsManagerRestClient: RestClient,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	fun loadForSync(): EvaluationSettings {
		val threshold = fetchContentThreshold()
		if (threshold == null) {
			log.warn("Evaluation aborted: CONTENT relevance threshold is missing or invalid")
			throw EvaluationSettingsException("CONTENT relevance threshold is missing or invalid")
		}
		val vector = fetchReferenceVector()
		if (vector == null) {
			log.warn("Evaluation aborted: reference context vector is not configured")
			throw EvaluationSettingsException("Reference context vector is not configured")
		}
		return EvaluationSettings(threshold, vector)
	}

	fun loadForAsync(): EvaluationSettings? {
		try {
			val threshold = fetchContentThreshold()
			val vector = fetchReferenceVector()
			if (threshold == null || vector == null) {
				log.warn(
					"Skipping async evaluation: thresholdPresent={} referenceVectorPresent={}",
					threshold != null,
					vector != null,
				)
				return null
			}
			return EvaluationSettings(threshold, vector)
		} catch (ex: Exception) {
			log.warn("Skipping async evaluation: failed to load settings: {}", ex.message)
			return null
		}
	}

	private fun fetchContentThreshold(): Double? =
		try {
			val dto = settingsManagerRestClient.get()
				.uri("/relevance-thresholds/CONTENT")
				.retrieve()
				.body(RelevanceThresholdItemDto::class.java)
			dto?.value
		} catch (_: RestClientResponseException) {
			null
		}

	private fun fetchReferenceVector(): List<Double>? {
		try {
			val entity = settingsManagerRestClient.get()
				.uri("/reference-context")
				.retrieve()
				.toEntity(ReferenceContextDto::class.java)
			if (entity.statusCode == HttpStatus.ACCEPTED) return null
			val body = entity.body ?: return null
			val v = body.vector
			return if (v.isNullOrEmpty()) null else v
		} catch (_: RestClientResponseException) {
			return null
		}
	}
}
