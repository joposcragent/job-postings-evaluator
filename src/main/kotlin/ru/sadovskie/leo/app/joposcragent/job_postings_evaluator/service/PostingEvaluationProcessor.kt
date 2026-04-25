package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service

import feign.FeignException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.EvaluationSettings
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.ExternalServiceException
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.SentenceTransformerFeignClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.SettingsHttpClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.TextVectorizeRequest
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.VectorsPairRequest
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.orchestration.OrchestrationFinishEventPublisher
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.repository.PostingEvaluationRepository
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.web.SyncEvaluationResultItem
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.tables.records.PostingsRecord
import java.util.UUID

@Service
class PostingEvaluationProcessor(
	private val settingsHttpClient: SettingsHttpClient,
	private val sentenceTransformerFeignClient: SentenceTransformerFeignClient,
	private val postingEvaluationRepository: PostingEvaluationRepository,
	private val orchestrationFinishEventPublisher: OrchestrationFinishEventPublisher,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	fun runSync(uuids: List<UUID>): List<SyncEvaluationResultItem> {
		val settings = settingsHttpClient.loadForSync()
		val rows = postingEvaluationRepository.findEvaluatableByUuids(uuids)
		if (rows.isEmpty()) throw NoPostingsToEvaluateException()
		return evaluateRows(settings, rows)
	}

	fun runSyncOne(jobPostingUuid: UUID, correlationId: UUID?): EvaluationStatus {
		if (correlationId == null) {
			return runSync(listOf(jobPostingUuid)).single().status
		}
		return try {
			val settings = settingsHttpClient.loadForSync()
			evaluateSingleWithOptionalSuccessFinish(jobPostingUuid, correlationId, settings)
		} catch (e: Exception) {
			publishFinishFailureBestEffort(correlationId, jobPostingUuid, e)
			throw e
		}
	}

	fun runAsync(uuids: List<UUID>, correlationId: UUID? = null) {
		if (uuids.size == 1 && correlationId != null) {
			val jobPostingUuid = uuids.single()
			try {
				val settings = settingsHttpClient.loadForAsync()
				if (settings == null) {
					log.warn("Async evaluation skipped: settings unavailable for {}", jobPostingUuid)
					publishFinishFailureBestEffort(
						correlationId,
						jobPostingUuid,
						IllegalStateException("Evaluation settings not available for async run"),
					)
					return
				}
				evaluateSingleWithOptionalSuccessFinish(jobPostingUuid, correlationId, settings)
			} catch (e: Exception) {
				publishFinishFailureBestEffort(correlationId, jobPostingUuid, e)
				if (e is NoPostingsToEvaluateException) {
					log.warn("Async evaluation: no evaluatable posting for {}", jobPostingUuid)
				} else {
					log.error("Async evaluation failed for {}", jobPostingUuid, e)
				}
			}
			return
		}

		val settings = settingsHttpClient.loadForAsync() ?: return
		val rows = postingEvaluationRepository.findEvaluatableByUuids(uuids)
		if (rows.isEmpty()) {
			log.warn("Async evaluation finished: no postings in NEW or PENDING for given UUIDs")
			return
		}
		evaluateRows(settings, rows)
	}

	fun runAsyncBatch(limit: Int) {
		val settings = settingsHttpClient.loadForAsync() ?: return
		val rows = postingEvaluationRepository.findFirstEvaluatable(limit)
		if (rows.isEmpty()) {
			log.warn("Async batch evaluation finished: no postings in NEW or PENDING")
			return
		}
		val results = evaluateRows(settings, rows)
		for (item in results) {
			log.info("Async batch: evaluated posting {} -> {}", item.uuid, item.status)
		}
	}

	private fun evaluateSingleWithOptionalSuccessFinish(
		jobPostingUuid: UUID,
		correlationId: UUID,
		settings: EvaluationSettings,
	): EvaluationStatus {
		val rows = postingEvaluationRepository.findEvaluatableByUuids(listOf(jobPostingUuid))
		if (rows.isEmpty()) throw NoPostingsToEvaluateException()
		val status = evaluateRows(settings, rows).single().status
		try {
			orchestrationFinishEventPublisher.publishSuccess(correlationId, jobPostingUuid, status)
		} catch (e: Exception) {
			log.error(
				"Failed to notify orchestrator of successful evaluation for posting {}",
				jobPostingUuid,
				e,
			)
		}
		return status
	}

	private fun publishFinishFailureBestEffort(
		correlationId: UUID,
		jobPostingUuid: UUID,
		error: Throwable,
	) {
		val message = error.message?.takeIf { it.isNotBlank() } ?: error.toString()
		try {
			orchestrationFinishEventPublisher.publishFailure(correlationId, jobPostingUuid, message)
		} catch (e: Exception) {
			log.warn(
				"Failed to send finish FAILED to orchestrator for posting {}",
				jobPostingUuid,
				e,
			)
		}
	}

	private fun evaluateRows(
		settings: EvaluationSettings,
		rows: List<PostingsRecord>,
	): List<SyncEvaluationResultItem> {
		val out = ArrayList<SyncEvaluationResultItem>(rows.size)
		for (row in rows) {
			val contentVector = resolveContentVector(row)
			val similarity = cosineSimilarityOrThrow(contentVector, settings.referenceVector)
			val status = if (similarity >= settings.threshold) {
				EvaluationStatus.RELEVANT
			} else {
				EvaluationStatus.IRRELEVANT
			}
			val vectorForDb = contentVector.map { it.toFloat() }.toTypedArray()
			postingEvaluationRepository.updateAfterEvaluation(
				row.uuid,
				status,
				similarity.toFloat(),
				vectorForDb,
			)
			out.add(SyncEvaluationResultItem(row.uuid, status))
		}
		return out
	}

	private fun resolveContentVector(row: PostingsRecord): List<Double> {
		val existing = row.contentVector
		if (existing != null && existing.isNotEmpty()) {
			return existing.map { it.toDouble() }
		}
		val text = row.content ?: ""
		return vectorizeOrThrow(text)
	}

	private fun vectorizeOrThrow(text: String): List<Double> =
		try {
			sentenceTransformerFeignClient.vectorize(TextVectorizeRequest(text))
		} catch (e: FeignException) {
			throw ExternalServiceException(
				"sentence-transformer vectorize failed: ${e.status()} ${e.contentUTF8()}",
				e,
			)
		}

	private fun cosineSimilarityOrThrow(left: List<Double>, right: List<Double>): Double =
		try {
			sentenceTransformerFeignClient.cosineSimilarity(VectorsPairRequest(left, right)).similarity
		} catch (e: FeignException) {
			throw ExternalServiceException(
				"sentence-transformer cosine-similarity failed: ${e.status()} ${e.contentUTF8()}",
				e,
			)
		}
}
