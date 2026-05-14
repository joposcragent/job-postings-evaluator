package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service

import feign.FeignException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client.SentenceTextFeignClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client.SentenceVectorsFeignClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client.SettingsReferenceContextFeignClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client.SettingsSearchQueryFeignClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.kafka.JobPostingEvaluateResultPublisher
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.store.PostingRow
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.store.PostingsRepository
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.sentence.model.TextCorpus
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.sentence.model.VectorsPair
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.settings.model.ReferenceContext
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.openapi.model.EvaluationStatus
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.openapi.model.SyncEvaluationResultItem
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.openapi.model.UuidsList
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus as JooqEvaluationStatus
import java.math.BigDecimal
import java.util.UUID

private val EligibleStatuses: Set<JooqEvaluationStatus> = setOf(JooqEvaluationStatus.NEW, JooqEvaluationStatus.PENDING)

@Service
class EvaluationService(
	private val postings: PostingsRepository,
	private val settingsRef: SettingsReferenceContextFeignClient,
	private val settingsSq: SettingsSearchQueryFeignClient,
	private val textApi: SentenceTextFeignClient,
	private val vectorsApi: SentenceVectorsFeignClient,
	private val evaluateResultPublisher: JobPostingEvaluateResultPublisher,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	fun evaluateSyncList(body: UuidsList): List<SyncEvaluationResultItem> {
		if (body.list.isEmpty()) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST)
		}
		log.info("evaluateSyncList start requestedUuids={}", body.list.size)
		val candidates = postings.findByUuidsEligibleForSync(body.list)
		if (candidates.isEmpty()) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND)
		}
		val out = candidates.map { p ->
			SyncEvaluationResultItem(p.uuid, evaluateAndPersist(p.uuid, null, null, forceReevaluate = false).status.toApi())
		}
		log.info("evaluateSyncList done evaluatedCount={}", out.size)
		return out
	}

	fun evaluateSyncOne(jobPostingUuid: UUID, correlationId: UUID?): EvaluationStatus {
		log.info(
			"evaluateSyncOne start jobPostingUuid={} correlationId={}",
			jobPostingUuid,
			correlationId?.toString() ?: "-",
		)
		return try {
			val out = evaluateAndPersist(jobPostingUuid, correlationId, null, forceReevaluate = true)
			log.info(
				"evaluateSyncOne done jobPostingUuid={} correlationId={} status={}",
				jobPostingUuid,
				correlationId?.toString() ?: "-",
				out.status,
			)
			out.status.toApi()
		} catch (e: Exception) {
			if (correlationId != null) {
				publishCorrelationFailed(correlationId, jobPostingUuid, e)
			}
			throw e
		}
	}

	fun evaluateFromKafkaBegin(jobUuid: UUID, jobPostingUuid: UUID) {
		log.info("kafka evaluate begin jobUuid={} jobPostingUuid={}", jobUuid, jobPostingUuid)
		try {
			val outcome = evaluateAndPersist(jobPostingUuid, null, null, forceReevaluate = true)
			evaluateResultPublisher.publishSucceeded(
				jobUuid,
				jobPostingUuid,
				outcome.status,
				outcome.relevance,
			)
		} catch (e: ResponseStatusException) {
			val msg = if (e.statusCode == HttpStatus.NOT_FOUND) {
				"Job posting $jobPostingUuid not found"
			} else {
				e.reason ?: e.message ?: e.toString()
			}
			log.error("kafka evaluate failed jobUuid={} jobPostingUuid={}", jobUuid, jobPostingUuid, e)
			evaluateResultPublisher.publishFailed(jobUuid, jobPostingUuid, msg)
		} catch (e: Exception) {
			log.error("kafka evaluate failed jobUuid={} jobPostingUuid={}", jobUuid, jobPostingUuid, e)
			evaluateResultPublisher.publishFailed(jobUuid, jobPostingUuid, e.message ?: e.toString())
		}
	}

	fun runBatchIfPossible(batchSize: Int) {
		log.info("runBatchIfPossible start batchSize={}", batchSize)
		if (batchSize < 1) {
			log.info("runBatchIfPossible skip batchSize < 1")
			return
		}
		val (refCtx, refVec) = try {
			fetchReferenceContextVector()
		} catch (e: Exception) {
			log.warn("Пакетная оценка: невозможно загрузить настройки: {}", e.toString(), e)
			return
		}
		val page = try {
			postings.listNewPendingOrderedLimit(batchSize)
		} catch (e: Exception) {
			log.warn("Пакетная оценка: ошибка выборки: {}", e.toString(), e)
			return
		}
		if (page.isEmpty()) {
			log.info("runBatchIfPossible done no candidates in NEW/PENDING")
			return
		}
		log.info("runBatchIfPossible candidatesCount={}", page.size)
		for (p in page) {
			try {
				val o = evaluateAndPersistWithPreloaded(p.uuid, null, refCtx, refVec, p, forceReevaluate = false)
				log.info("Пакетная оценка: uuid={} -> {}", p.uuid, o.status)
			} catch (e: Exception) {
				log.error("Пакетная оценка: сбой для {}: {}", p.uuid, e.toString(), e)
			}
		}
		log.info("runBatchIfPossible done processedCount={}", page.size)
	}

	private data class Outcome(val status: JooqEvaluationStatus, val relevance: Double)

	private data class PreloadedConfig(
		val refContext: String,
		val refVector: List<Double>,
		val posting: PostingRow,
	)

	private fun evaluateAndPersist(
		jobPostingUuid: UUID,
		correlationId: UUID?,
		pre: PreloadedConfig?,
		forceReevaluate: Boolean,
	): Outcome {
		log.info(
			"eval start jobPostingUuid={} correlationId={} batchPreloaded={}",
			jobPostingUuid,
			correlationId?.toString() ?: "-",
			pre != null,
		)
		val (refContext, refVector) = if (pre != null) {
			pre.refContext to pre.refVector
		} else {
			logStep("settings.getReferenceContext", jobPostingUuid, correlationId) { fetchReferenceContextVector() }
		}
		val item: PostingRow = if (pre?.posting != null) {
			if (!forceReevaluate && pre.posting.evaluationStatus !in EligibleStatuses) {
				throw ResponseStatusException(HttpStatus.NOT_FOUND)
			}
			if (pre.posting.uuid != jobPostingUuid) {
				throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)
			}
			pre.posting
		} else {
			logStep("postings.get", jobPostingUuid, correlationId) {
				getPostingOrNotFound(jobPostingUuid, forceReevaluate)
			}
		}
		if (!forceReevaluate && item.evaluationStatus !in EligibleStatuses) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND)
		}
		return evaluateCoreWithRefs(item, refVector, jobPostingUuid, correlationId)
	}

	private fun evaluateAndPersistWithPreloaded(
		jobPostingUuid: UUID,
		correlationId: UUID?,
		refContext: String,
		refVector: List<Double>,
		posting: PostingRow,
		forceReevaluate: Boolean,
	): Outcome = evaluateAndPersist(
		jobPostingUuid,
		correlationId,
		PreloadedConfig(refContext, refVector, posting),
		forceReevaluate,
	)

	private fun evaluateCoreWithRefs(
		item: PostingRow,
		refVector: List<Double>,
		jobPostingUuid: UUID,
		correlationId: UUID?,
	): Outcome {
		val contentThreshold = logStep("settings.getSearchQuery", jobPostingUuid, correlationId) {
			fetchContentRelevanceThreshold(item.searchQueryUuid)
		}
		val (vector, sim) = computeVectorAndSimilarity(item, refVector, jobPostingUuid, correlationId)
		val relevant = sim >= contentThreshold
		val st = if (relevant) JooqEvaluationStatus.RELEVANT else JooqEvaluationStatus.IRRELEVANT
		logStep("postings.update", jobPostingUuid, correlationId) {
			postings.updateAfterEvaluation(jobPostingUuid, vector, sim, st)
		}
		if (correlationId != null) {
			publishCorrelationSucceeded(correlationId, jobPostingUuid, st, sim)
		}
		log.info(
			"eval done jobPostingUuid={} correlationId={} status={}",
			jobPostingUuid,
			correlationId?.toString() ?: "-",
			st,
		)
		return Outcome(st, sim)
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

	private fun getPostingOrNotFound(uuid: UUID, forceReevaluate: Boolean): PostingRow {
		val row = postings.findByUuid(uuid) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
		if (!forceReevaluate && row.evaluationStatus !in EligibleStatuses) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND)
		}
		return row
	}

	private fun fetchReferenceContextVector(): Pair<String, List<Double>> {
		val ref: ReferenceContext = try {
			val res = settingsRef.getReferenceContext()
			when (res.statusCode.value()) {
				200 -> res.body ?: throw ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"Empty reference context from settings",
				)
				404, 202 -> throw ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"Reference context is not available",
				)
				in 500..599 -> throw ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"Downstream service error: HTTP " + res.statusCode.value(),
				)
				else -> throw ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"Unexpected status from settings: " + res.statusCode.value(),
				)
			}
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
		} catch (e: ResponseStatusException) {
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
		return ref.context to ref.vector.map { it.toDouble() }
	}

	private fun fetchContentRelevanceThreshold(searchQueryUuid: UUID): Double {
		val sq = try {
			val res = settingsSq.getSearchQuery(searchQueryUuid)
			if (res.statusCode.value() == 404) {
				throw ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"Search query is not available for uuid $searchQueryUuid",
				)
			}
			if (!res.statusCode.is2xxSuccessful || res.body == null) {
				throw ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"Search query unexpected status: " + res.statusCode.value(),
				)
			}
			res.body!!
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
		} catch (e: ResponseStatusException) {
			throw e
		} catch (e: Exception) {
			throw ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Search query is not available: " + (e.message ?: e.toString()),
				e,
			)
		}
		val v = sq.contentRelevance.toDouble()
		if (!v.isFinite() || v !in 0.0..1.0) {
			throw ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Invalid content relevance from settings for uuid $searchQueryUuid",
			)
		}
		return v
	}

	private fun computeVectorAndSimilarity(
		item: PostingRow,
		refVector: List<Double>,
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
					val tc = TextCorpus()
					tc.setText(item.content)
					val res = textApi.vectorize(tc)
					if (!res.statusCode.is2xxSuccessful || res.body == null) {
						if (res.statusCode.value() == 413) {
							throw ResponseStatusException(
								HttpStatus.INTERNAL_SERVER_ERROR,
								"Content text too long for embedding",
							)
						}
						throw ResponseStatusException(
							HttpStatus.INTERNAL_SERVER_ERROR,
							"Vectorize failed: HTTP " + res.statusCode.value(),
						)
					}
					res.body!!.map { it.toDouble() }
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
		if (v.size != refVector.size) {
			throw ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Vector dimension mismatch between posting and reference",
			)
		}
		val sim = logStep("sentence_transformer.cosineSimilarity", jobPostingUuid, correlationId) {
			try {
				val pair = VectorsPair()
				pair.left = v.map { BigDecimal.valueOf(it) }
				pair.right = refVector.map { BigDecimal.valueOf(it) }
				val res = vectorsApi.cosineSimilarity(pair)
				if (!res.statusCode.is2xxSuccessful || res.body == null) {
					throw ResponseStatusException(
						HttpStatus.INTERNAL_SERVER_ERROR,
						"Cosine similarity failed: HTTP " + res.statusCode.value(),
					)
				}
				res.body!!.similarity.toDouble()
			} catch (e: FeignException) {
				maybeRethrowDownstream5xx(e)
				throw e
			} catch (e: Exception) {
				throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.message, e)
			}
		}
		return v to sim
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

	private fun publishCorrelationSucceeded(
		jobUuid: UUID,
		jobPostingUuid: UUID,
		status: JooqEvaluationStatus,
		relevance: Double,
	) {
		val cid = jobUuid.toString()
		val start = System.nanoTime()
		try {
			evaluateResultPublisher.publishSucceeded(jobUuid, jobPostingUuid, status, relevance)
			val ms = (System.nanoTime() - start) / 1_000_000L
			log.info(
				"eval step=kafka.evaluateResult jobPostingUuid={} correlationId={} durationMs={} outcome=success state=SUCCEEDED",
				jobPostingUuid,
				cid,
				ms,
			)
		} catch (e: Exception) {
			val ms = (System.nanoTime() - start) / 1_000_000L
			log.info(
				"eval step=kafka.evaluateResult jobPostingUuid={} correlationId={} durationMs={} outcome=error",
				jobPostingUuid,
				cid,
				ms,
			)
			log.error("Kafka evaluate result publish: {}", e.toString(), e)
		}
	}

	private fun publishCorrelationFailed(jobUuid: UUID, jobPostingUuid: UUID, exception: Throwable) {
		val cid = jobUuid.toString()
		val start = System.nanoTime()
		try {
			val logText = exception.toString() + (exception.cause?.let { "\nCause: " + it } ?: "")
			evaluateResultPublisher.publishFailed(jobUuid, jobPostingUuid, logText)
			val ms = (System.nanoTime() - start) / 1_000_000L
			log.info(
				"eval step=kafka.evaluateResult jobPostingUuid={} correlationId={} durationMs={} outcome=success state=FAILED",
				jobPostingUuid,
				cid,
				ms,
			)
		} catch (e: Exception) {
			val ms = (System.nanoTime() - start) / 1_000_000L
			log.info(
				"eval step=kafka.evaluateResult jobPostingUuid={} correlationId={} durationMs={} outcome=error state=FAILED",
				jobPostingUuid,
				cid,
				ms,
			)
			log.error("Kafka evaluate result publish: {}", e.toString(), e)
		}
	}
}

private fun JooqEvaluationStatus.toApi(): EvaluationStatus = when (this) {
	JooqEvaluationStatus.NEW -> EvaluationStatus.NEW
	JooqEvaluationStatus.PENDING -> EvaluationStatus.PENDING
	JooqEvaluationStatus.IRRELEVANT -> EvaluationStatus.IRRELEVANT
	JooqEvaluationStatus.RELEVANT -> EvaluationStatus.RELEVANT
}
