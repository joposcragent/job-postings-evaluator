package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.UuidsListRequest
import java.util.UUID

@Component
class AsyncEvaluationRunner(
	private val evaluation: EvaluationService,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	@Async("evaluationExecutor")
	fun runList(body: UuidsListRequest) {
		runCatching { evaluation.evaluateSyncList(body) }
			.onFailure { log.error("Async evaluate list failed: {}", it.toString(), it) }
	}

	@Async("evaluationExecutor")
	fun runOne(jobPostingUuid: UUID, correlationId: UUID?) {
		runCatching { evaluation.evaluateSyncOne(jobPostingUuid, correlationId) }
			.onFailure { log.error("Async evaluate one {} failed: {}", jobPostingUuid, it.toString(), it) }
	}

	@Async("evaluationExecutor")
	fun runBatch(size: Int) {
		runCatching { evaluation.runBatchIfPossible(size) }
			.onFailure { log.error("Async batch size={} failed: {}", size, it.toString(), it) }
	}
}
