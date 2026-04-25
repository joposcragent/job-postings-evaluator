package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.orchestration

import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import java.util.UUID

/**
 * Publishes `task.finish` to celery-orchestrator via `POST /events-queue/finish`.
 */
interface OrchestrationFinishEventPublisher {

	fun publishSuccess(
		correlationId: UUID,
		jobPostingUuid: UUID,
		evaluationStatus: EvaluationStatus,
	)

	fun publishFailure(
		correlationId: UUID,
		jobPostingUuid: UUID,
		executionLog: String,
	)
}
