package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class AsyncEvaluationRunner(
	private val postingEvaluationProcessor: PostingEvaluationProcessor,
) {

	@Async
	fun runList(uuids: List<UUID>) {
		postingEvaluationProcessor.runAsync(uuids)
	}

	@Async
	fun runOne(jobPostingUuid: UUID, correlationId: UUID? = null) {
		postingEvaluationProcessor.runAsync(listOf(jobPostingUuid), correlationId)
	}

	@Async
	fun runBatch(size: Int) {
		postingEvaluationProcessor.runAsyncBatch(size)
	}
}
