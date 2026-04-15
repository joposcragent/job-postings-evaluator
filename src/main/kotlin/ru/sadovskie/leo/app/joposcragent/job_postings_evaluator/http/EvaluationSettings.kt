package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http

data class EvaluationSettings(
	val threshold: Double,
	val referenceVector: List<Double>,
)
