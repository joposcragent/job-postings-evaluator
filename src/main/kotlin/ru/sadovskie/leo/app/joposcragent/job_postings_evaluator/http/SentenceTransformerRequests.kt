package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http

import com.fasterxml.jackson.annotation.JsonProperty

/** JSON body for POST /text/vectorize (matches sentence-transformer TextCorpus). */
data class TextVectorizeRequest(
	@JsonProperty("text")
	val text: String,
)

/** JSON body for POST /vectors/cosine-similarity (matches VectorsPair). */
data class VectorsPairRequest(
	@JsonProperty("left")
	val left: List<Double>,
	@JsonProperty("right")
	val right: List<Double>,
)
