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
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.JobPostingsList
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.ReferenceContext
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.RelevanceThresholdItem
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
		val fromCrud = feignListOrNotFound { jobPostingsCrud.findByUuids(UuidsListRequest(body.list)) }
		val candidates = fromCrud.list
			.filter { it.evaluationStatus != null && it.evaluationStatus in Eligible }
			.sortedBy { it.uuid }
		if (candidates.isEmpty()) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND)
		}
		return candidates.map { p ->
			SyncEvaluationResultItem(p.uuid, evaluateAndPersist(p.uuid, null, null).status)
		}
	}

	fun evaluateSyncOne(jobPostingUuid: UUID, correlationId: UUID?): ApiEvaluationStatus = try {
		val out = evaluateAndPersist(jobPostingUuid, correlationId, null)
		out.status
	} catch (e: Exception) {
		if (correlationId != null) {
			finishToOrchestrator(correlationId, jobPostingUuid, "FAILED", e)
		}
		throw e
	}

	fun runBatchIfPossible(batchSize: Int) {
		val (ctx, thr) = try {
			loadSettingsOrThrow()
		} catch (e: Exception) {
			log.warn("Пакетная оценка: невозможно загрузить настройки: {}", e.toString(), e)
			return
		}
		if (batchSize < 1) {
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
		} ?: return

		if (page.list.isEmpty()) {
			log.warn("Пакетная оценка: вакансий в статусах NEW/PENDING не найдено")
			return
		}
		for (p in page.list) {
			try {
				val o = evaluateAndPersist(p.uuid, null, PreloadedConfig(ctx, thr, p))
				log.info("Пакетная оценка: uuid={} -> {}", p.uuid, o.status)
			} catch (e: Exception) {
				log.error("Пакетная оценка: сбой для {}: {}", p.uuid, e.toString(), e)
			}
		}
	}

	/**
	 * @param pre if non-null, reuse settings and posting to avoid refetching (batch).
	 * @return outcome with status; for sync single with [correlationId] sends SUCCEEDED to orchestrator on success.
	 */
	private fun evaluateAndPersist(
		jobPostingUuid: UUID,
		correlationId: UUID?,
		pre: PreloadedConfig?,
	): Outcome {
		val (ref, thr) = if (pre != null) (pre.context to pre.threshold) else loadSettingsOrThrow()
		val item: JobPostingsItem
		if (pre?.posting != null) {
			if (pre.posting.evaluationStatus == null || pre.posting.evaluationStatus !in Eligible) {
				throw ResponseStatusException(HttpStatus.NOT_FOUND)
			}
			if (pre.posting.uuid != jobPostingUuid) {
				throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)
			}
			item = pre.posting
		} else {
			item = getPostingOrNotFound(jobPostingUuid)
		}
		if (item.evaluationStatus == null || item.evaluationStatus !in Eligible) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND)
		}
		val (vector, sim) = computeVectorAndSimilarity(item, ref)
		val t = thr.value
		val relevant = if (t > 1) sim * 100.0 >= t else sim >= t
		val st = if (relevant) ApiEvaluationStatus.RELEVANT else ApiEvaluationStatus.IRRELEVANT
		patchOrThrow(
			jobPostingUuid,
			JobPostingsItemPatch(
				contentVector = vector,
				relevance = sim,
				evaluationStatus = st,
			),
		)
		if (correlationId != null) {
			finishToOrchestrator(correlationId, jobPostingUuid, "SUCCEEDED", null)
		}
		return Outcome(st)
	}

	private data class PreloadedConfig(
		val context: ReferenceContext,
		val threshold: RelevanceThresholdItem,
		val posting: JobPostingsItem? = null,
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

	private fun loadSettingsOrThrow(): Pair<ReferenceContext, RelevanceThresholdItem> {
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
		val thr = try {
			settings.getRelevanceThreshold("CONTENT")
		} catch (e: FeignException) {
			if (e.status() == 404) {
				throw ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"Relevance threshold CONTENT is not set",
					e,
				)
			}
			maybeRethrowDownstream5xx(e)
			throw e
		}
		return ref to thr
	}

	private fun computeVectorAndSimilarity(item: JobPostingsItem, ref: ReferenceContext): Pair<List<Double>, Double> {
		val v: List<Double>
		if (item.contentVector.isNullOrEmpty()) {
			if (item.content.isNullOrBlank()) {
				throw ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"Empty content; cannot build embedding for posting " + item.uuid,
				)
			}
			v = try {
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
		} else {
			v = item.contentVector
		}
		if (v.size != ref.vector.size) {
			throw ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Vector dimension mismatch between posting and reference",
			)
		}
		return v to try {
			sentence.cosineSimilarity(VectorsPair(left = v, right = ref.vector)).similarity
		} catch (e: FeignException) {
			maybeRethrowDownstream5xx(e)
			throw e
		} catch (e: Exception) {
			throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.message, e)
		}
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
	) = try {
		val logText = if (state == "FAILED" && exception != null) (exception.toString() + (exception.cause?.let { "\nCause: " + it } ?: "")) else null
		orchestrator.finishEvent(
			FinishEventRequest(
				correlationId = correlationId,
				status = state,
				createdAt = Instant.now(),
				jobPostingUuid = jobPostingUuid,
				executionLog = logText,
			),
		)
	} catch (e: Exception) {
		log.error("Celery orchestrator finish: {}", e.toString(), e)
	}
}
