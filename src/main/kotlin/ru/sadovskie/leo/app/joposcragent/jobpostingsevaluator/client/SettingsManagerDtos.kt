package ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class RelevanceThresholdItem(
	val value: Double,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ReferenceContextBody(
	val vector: List<Double>?,
)
