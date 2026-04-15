package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.web

import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus

data class SyncSingleEvaluationResponse(
	val status: EvaluationStatus,
)
