package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.web

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class BatchAsyncProcessingParametersRequest(
	@field:NotNull
	@field:Min(1)
	val size: Int,
)
