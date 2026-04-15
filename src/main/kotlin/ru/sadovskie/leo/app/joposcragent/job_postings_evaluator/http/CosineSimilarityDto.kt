package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CosineSimilarityDto(
	val similarity: Double,
)
