package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service

import feign.FeignException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client.CeleryOrchestratorClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client.JobPostingsCrudClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client.SentenceTransformerClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client.SettingsManagerClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.ApiEvaluationStatus
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.FinishEventRequest
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.JobPostingsItem
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.JobPostingsItemPatch
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.ReferenceContext
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.SyncEvaluationResultItem
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.TextCorpus
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.UuidsListRequest
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.VectorsPair
import java.time.Instant
import java.util.UUID

private val Eligible: Set<ApiEvaluationStatus> = setOf(ApiEvaluationStatus.NEW, ApiEvaluationStatus.PENDING)

@Service
class EvaluationService(
	private val jobPostingsCrud: JobPostingsCrudClient,
	private val settings: SettingsManagerClient,
	private val sentence: SentenceTransformerClient,
	private val orchestrator: CeleryOrchestratorClient,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	fun evaluateSyncList(body: UuidsListRequest): List<SyncEvaluationResultItem> {
		if (body.list.isEmpty()) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST)
		}
		log.info("evaluateSyncList start requestedUuids={}", body.list.size)
		val fromCrud = feignListOrNotFound { jobPostingsCrud.findByUuids(UuidsListRequest(body.list)) }
		val candidates = fromCrud.list
			.filter { it.evaluationStatus != null && it.evaluationStatus in Eligible }
			.sortedBy { it.uuid }
		if (candidates.isEmpty()) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND)
		}
		val out = candidates.map { p ->
			SyncEvaluationResultItem(p.uuid, evaluateAndPersist(p.uuid, null, null).status)
		}
		log.info("evaluateSyncList done evaluatedCount={}", out.size)
		return out
	}

	fun evaluateSyncOne(jobPostingUuid: UUID, correlationId: UUID?): ApiEvaluationStatus {
		log.info(
			"evaluateSyncOne start jobPostingUuid={} correlationId={}",
			jobPostingUuid,
			correlationId?.toString() ?: "-",
		)
		return try {
			val out = evaluateAndPersist(jobPostingUuid, correlationId, null)
			log.info(
				"evaluateSyncOne done jobPostingUuid={} correlationId={} status={}",
				jobPostingUuid,
				correlationId?.toString() ?: "-",
				out.status,
			)
			out.status
		} catch (e: Exception) {
			if (correlationId != null) {
				finishToOrchestrator(correlationId, jobPostingUuid, "FAILED", e)
			}
			throw e
		}
	}

	fun runBatchIfPossible(batchSize: Int) {
		log.info("runBatchIfPossible start batchSize={}", batchSize)
		if (batchSize < 1) {
			log.info("runBatchIfPossible skip batchSize < 1")
			return
		}
		val ctx = try {
			fetchReferenceContext()
		} catch (e: Exception) {
			log.warn("Пакетная оценка: невозможно загрузить настройки: {}", e.toString(), e)
			return
		}
		val page = feignListOrNull {
			jobPostingsCrud.list(
				uuid = null,
				uid = null,
				title = null,
				company = null,
				evaluationStatuses = listOf(ApiEvaluationStatus.NEW, ApiEvaluationStatus.PENDING),
				page = 1,
				size = batchSize,
			)
		} ?: run {
			log.info("runBatchIfPossible done listCallFailedOrNull candidatesCount=0")
			return
		}

		if (page.list.isEmpty()) {
			log.info("runBatchIfPossible done no candidates in NEW/PENDING")
			return
		}
		log.info("runBatchIfPossible candidatesCount={}", page.list.size)
		for (p in page.list) {
			try {
				val o = evaluateAndPersist(p.uuid, null, PreloadedConfig(ctx, p))
				log.info("Пакетная оценка: uuid={} -> {}", p.uuid, o.status)
			} catch (e: Exception) {
				log.error("Пакетная оценка: сбой для {}: {}", p.uuid, e.toString(), e)
			}
		}
		log.info("runBatchIfPossible done processedCount={}", page.list.size)
	}

	/**
	 * @param pre if non-null, reuse reference context and posting from list (batch).
	 * @return outcome with status; for sync single with [correlationId] sends SUCCEEDED to orchestrator on success.
	 */
	private fun evaluateAndPersist(
		jobPostingUuid: UUID,
		correlationId: UUID?,
		pre: PreloadedConfig?,
	): Outcome {
		log.info(
			"eval start jobPostingUuid={} correlationId={} batchPreloaded={}",
			jobPostingUuid,
			correlationId?.toString() ?: "-",
			pre != null,
		)
		val ref = if (pre != null) {
			pre.context
		} else {
			logStep("settings.getReferenceContext", jobPostingUuid, correlationId) { fetchReferenceContext() }
		}
		val item: JobPostingsItem = if (pre?.posting != null) {
			if (pre.posting.evaluationStatus == null || pre.posting.evaluationStatus !in Eligible) {
				throw ResponseStatusException(HttpStatus.NOT_FOUND)
			}
			if (pre.posting.uuid != jobPostingUuid) {
				throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)
			}
			pre.posting
		} else {
			logStep("job_postings_crud.get", jobPostingUuid, correlationId) { getPostingOrNotFound(jobPostingUuid) }
		}
		if (item.evaluationStatus == null || item.evaluationStatus !in Eligible) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND)
		}
		val contentThreshold = logStep("settings.getSearchQuery", jobPostingUuid, correlationId) {
			fetchContentRelevanceThreshold(item.searchQueryUuid)
		}
		val (vector, sim) = computeVectorAndSimilarity(item, ref, jobPostingUuid, correlationId)
		val relevant = sim >= contentThreshold
		val st = if (relevant) ApiEvaluationStatus.RELEVANT else ApiEvaluationStatus.IRRELEVANT
		logStep("job_postings_crud.patch", jobPostingUuid, correlationId) {
			patchOrThrow(
				jobPostingUuid,
				JobPostingsItemPatch(
					contentVector = vector,
					relevance = sim,
					evaluationStatus = st,
				),
			)
		}
		if (correlationId != null) {
			finishToOrchestrator(correlationId, jobPostingUuid, "SUCCEEDED", null)
		}
		log.info(
			"eval done jobPostingUuid={} correlationId={} status={}",
			jobPostingUuid,
			correlationId?.toString() ?: "-",
			st,
		)
		return Outcome(st)
	}

	private fun <T> logStep(
		step: String,
		jobPostingUuid: UUID,
		correlationId: UUID?,
		block: () -> T,
	): T {
		val cid = correlationId?.toString() ?: "-"
		val start = System.nanoTime()
		return try {
			val result = block()
			val ms = (System.nanoTime() - start) / 1_000_000L
			log.info(
				"eval step={} jobPostingUuid={} correlationId={} durationMs={} outcome=success",
				step,
				jobPostingUuid,
				cid,
				ms,
			)
			result
		} catch (e: Exception) {
			val ms = (System.nanoTime() - start) / 1_000_000L
			log.info(
				"eval step={} jobPostingUuid={} correlationId={} durationMs={} outcome=error",
				step,
				jobPostingUuid,
				cid,
				ms,
			)
			throw e
		}
	}

	private data class PreloadedConfig(
		val context: ReferenceContext,
		val posting: JobPostingsItem,
	)

	private data class Outcome(val status: ApiEvaluationStatus)

	private fun getPostingOrNotFound(uuid: UUID) = try {
		jobPostingsCrud.getByUuid(uuid)
	} catch (e: FeignException) {
		if (e.status() == 404) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND)
		}
		maybeRethrowDownstream5xx(e)
		throw e
	}

	private fun fetchReferenceContext(): ReferenceContext {
		val ref = try {
			settings.getReferenceContext()
		} catch (e: FeignException) {
			if (e.status() == 404 || e.status() == 202) {
				throw ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"Reference context is not available",
					e,
				)
			}
			maybeRethrowDownstream5xx(e)
			throw e
		} catch (e: Exception) {
			throw ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Reference context is not available: " + (e.message ?: e.toString()),
				e,
			)
		}
		if (ref.vector.isEmpty() || ref.context.isBlank()) {
			throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid reference context from settings")
		}
		return ref
	}

	private fun fetchContentRelevanceThreshold(searchQueryUuid: UUID): Double {
		val sq = try {
			settings.getSearchQuery(searchQueryUuid)
		} catch (e: FeignException) {
			if (e.status() == 404) {
				throw ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"Search query is not available for uuid $searchQueryUuid",
					e,
				)
			}
			maybeRethrowDownstream5xx(e)
			throw e
		} catch (e: Exception) {
			throw ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Search query is not available: " + (e.message ?: e.toString()),
				e,
			)
		}
		val v = sq.contentRelevance
		if (!v.isFinite() || v !in 0.0..1.0) {
			throw ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Invalid content relevance from settings for uuid $searchQueryUuid",
			)
		}
		return v
	}

	private fun computeVectorAndSimilarity(
		item: JobPostingsItem,
		ref: ReferenceContext,
		jobPostingUuid: UUID,
		correlationId: UUID?,
	): Pair<List<Double>, Double> {
		val cid = correlationId?.toString() ?: "-"
		val v: List<Double>
		if (item.contentVector.isNullOrEmpty()) {
			if (item.content.isNullOrBlank()) {
				throw ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"Empty content; cannot build embedding for posting " + item.uuid,
				)
			}
			v = logStep("sentence_transformer.vectorize", jobPostingUuid, correlationId) {
				try {
					sentence.vectorize(TextCorpus(item.content))
				} catch (e: FeignException) {
					if (e.status() == 413) {
						throw ResponseStatusException(
							HttpStatus.INTERNAL_SERVER_ERROR,
							"Content text too long for embedding",
							e,
						)
					}
					maybeRethrowDownstream5xx(e)
					throw e
				} catch (e: Exception) {
					throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.message, e)
				}
			}
		} else {
			v = item.contentVector
			log.info(
				"eval step=sentence_transformer.embedding_reused jobPostingUuid={} correlationId={} durationMs=0 outcome=success",
				jobPostingUuid,
				cid,
			)
		}
		if (v.size != ref.vector.size) {
			throw ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Vector dimension mismatch between posting and reference",
			)
		}
		val sim = logStep("sentence_transformer.cosineSimilarity", jobPostingUuid, correlationId) {
			try {
				sentence.cosineSimilarity(VectorsPair(left = v, right = ref.vector)).similarity
			} catch (e: FeignException) {
				maybeRethrowDownstream5xx(e)
				throw e
			} catch (e: Exception) {
				throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.message, e)
			}
		}
		return v to sim
	}

	private fun patchOrThrow(jobPostingUuid: UUID, p: JobPostingsItemPatch) = try {
		jobPostingsCrud.patch(jobPostingUuid, p)
	} catch (e: FeignException) {
		if (e.status() == 404) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND)
		}
		maybeRethrowDownstream5xx(e)
		throw e
	}

	fun <T> feignListOrNotFound(block: () -> T): T = try {
		block()
	} catch (e: FeignException) {
		if (e.status() == 404) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND)
		}
		maybeRethrowDownstream5xx(e)
		throw e
	}

	fun <T> feignListOrNull(block: () -> T): T? {
		return try {
			block()
		} catch (e: FeignException) {
			if (e.status() == 404) {
				return null
			}
			log.warn("List request: {}", e.toString(), e)
			null
		} catch (e: Exception) {
			log.error("List request: {}", e.toString(), e)
			null
		}
	}

	private fun maybeRethrowDownstream5xx(e: FeignException) {
		if (e.status() in 500..599) {
			throw ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Downstream service error: HTTP " + e.status() + (e.contentUTF8()?.let { " body: " + it } ?: ""),
				e,
			)
		}
	}

	private fun finishToOrchestrator(
		correlationId: UUID,
		jobPostingUuid: UUID,
		state: String,
		exception: Throwable? = null,
	) {
		val cid = correlationId.toString()
		val start = System.nanoTime()
		try {
			val logText = if (state == "FAILED" && exception != null) {
				(exception.toString() + (exception.cause?.let { "\nCause: " + it } ?: ""))
			} else {
				null
			}
			orchestrator.finishEvent(
				FinishEventRequest(
					correlationId = correlationId,
					status = state,
					createdAt = Instant.now(),
					jobPostingUuid = jobPostingUuid,
					executionLog = logText,
				),
			)
			val ms = (System.nanoTime() - start) / 1_000_000L
			log.info(
				"eval step=celery_orchestrator.finishEvent jobPostingUuid={} correlationId={} durationMs={} outcome=success state={}",
				jobPostingUuid,
				cid,
				ms,
				state,
			)
		} catch (e: Exception) {
			val ms = (System.nanoTime() - start) / 1_000_000L
			log.info(
				"eval step=celery_orchestrator.finishEvent jobPostingUuid={} correlationId={} durationMs={} outcome=error state={}",
				jobPostingUuid,
				cid,
				ms,
				state,
			)
			log.error("Celery orchestrator finish: {}", e.toString(), e)
		}
	}
}
