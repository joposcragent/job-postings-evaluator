package ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.dto

import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import java.util.UUID

data class JobPostingsUidsList(
	val list: List<String>,
)

data class JobPostingsUidsEvaluatedList(
	val list: List<JobPostingsUidsEvaluatedItem>,
)

data class JobPostingsUidsEvaluatedItem(
	val uuid: UUID,
	val evaluationStatus: EvaluationStatus,
)
