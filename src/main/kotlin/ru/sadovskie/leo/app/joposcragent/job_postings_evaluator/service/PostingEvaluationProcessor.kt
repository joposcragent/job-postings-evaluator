package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.EvaluationSettings
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.SentenceTransformerHttpClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.SettingsHttpClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.repository.PostingEvaluationRepository
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.web.SyncEvaluationResultItem
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.tables.records.PostingsRecord
import java.util.UUID

@Service
class PostingEvaluationProcessor(
	private val settingsHttpClient: SettingsHttpClient,
	private val sentenceTransformerHttpClient: SentenceTransformerHttpClient,
	private val postingEvaluationRepository: PostingEvaluationRepository,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	@Transactional
	fun runSync(uuids: List<UUID>): List<SyncEvaluationResultItem> {
		val settings = settingsHttpClient.loadForSync()
		val rows = postingEvaluationRepository.findEvaluatableByUuids(uuids)
		if (rows.isEmpty()) throw NoPostingsToEvaluateException()
		return evaluateRows(settings, rows)
	}

	@Transactional
	fun runAsync(uuids: List<UUID>) {
		val settings = settingsHttpClient.loadForAsync() ?: return
		val rows = postingEvaluationRepository.findEvaluatableByUuids(uuids)
		if (rows.isEmpty()) {
			log.warn("Async evaluation finished: no postings in NEW or PENDING for given UUIDs")
			return
		}
		evaluateRows(settings, rows)
	}

	private fun evaluateRows(
		settings: EvaluationSettings,
		rows: List<PostingsRecord>,
	): List<SyncEvaluationResultItem> {
		val out = ArrayList<SyncEvaluationResultItem>(rows.size)
		for (row in rows) {
			val contentVector = resolveContentVector(row)
			val similarity = sentenceTransformerHttpClient.cosineSimilarity(
				contentVector,
				settings.referenceVector,
			)
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
		return sentenceTransformerHttpClient.vectorize(text)
	}
}
