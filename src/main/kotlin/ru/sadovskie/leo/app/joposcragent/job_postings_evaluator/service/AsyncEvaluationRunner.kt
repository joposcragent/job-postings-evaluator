package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.openapi.model.UuidsList
import java.util.UUID

@Component
class AsyncEvaluationRunner(
	private val evaluation: EvaluationService,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	@Async("evaluationExecutor")
	fun runList(body: UuidsList) {
		log.info("async evaluate list accepted requestedUuids={}", body.list.size)
		runCatching { evaluation.evaluateSyncList(body) }
			.onSuccess { log.info("async evaluate list completed requestedUuids={}", body.list.size) }
			.onFailure { log.error("Async evaluate list failed: {}", it.toString(), it) }
	}

	@Async("evaluationExecutor")
	fun runOne(jobPostingUuid: UUID, correlationId: UUID?) {
		log.info(
			"async evaluate one accepted jobPostingUuid={} correlationId={}",
			jobPostingUuid,
			correlationId?.toString() ?: "-",
		)
		runCatching { evaluation.evaluateSyncOne(jobPostingUuid, correlationId) }
			.onSuccess {
				log.info(
					"async evaluate one completed jobPostingUuid={} correlationId={} status={}",
					jobPostingUuid,
					correlationId?.toString() ?: "-",
					it,
				)
			}
			.onFailure { log.error("Async evaluate one {} failed: {}", jobPostingUuid, it.toString(), it) }
	}

	@Async("evaluationExecutor")
	fun runBatch(size: Int) {
		log.info("async evaluate batch accepted size={}", size)
		runCatching { evaluation.runBatchIfPossible(size) }
			.onSuccess { log.info("async evaluate batch completed size={}", size) }
			.onFailure { log.error("Async batch size={} failed: {}", size, it.toString(), it) }
	}
}
