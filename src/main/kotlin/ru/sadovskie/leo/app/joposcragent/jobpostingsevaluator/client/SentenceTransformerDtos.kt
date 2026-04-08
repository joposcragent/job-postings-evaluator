package ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CosineSimilarityResponse(
	val similarity: Double,
)
