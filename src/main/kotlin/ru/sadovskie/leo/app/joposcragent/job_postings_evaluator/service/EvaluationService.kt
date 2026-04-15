package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service

import org.springframework.stereotype.Service
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.web.SyncEvaluationResultItem
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import java.util.UUID

@Service
class EvaluationService(
	private val postingEvaluationProcessor: PostingEvaluationProcessor,
	private val asyncEvaluationRunner: AsyncEvaluationRunner,
) {

	fun evaluateSyncList(uuids: List<UUID>): List<SyncEvaluationResultItem> =
		postingEvaluationProcessor.runSync(uuids)

	fun evaluateSyncOne(jobPostingUuid: UUID): EvaluationStatus =
		postingEvaluationProcessor.runSync(listOf(jobPostingUuid)).single().status

	fun submitAsyncList(uuids: List<UUID>) {
		asyncEvaluationRunner.runList(uuids)
	}

	fun submitAsyncOne(jobPostingUuid: UUID) {
		asyncEvaluationRunner.runOne(jobPostingUuid)
	}
}
