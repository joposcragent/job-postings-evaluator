package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.web

import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import java.util.UUID

data class SyncEvaluationResultItem(
	val uuid: UUID,
	val status: EvaluationStatus,
)
