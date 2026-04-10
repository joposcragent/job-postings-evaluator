package ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.SentenceTransformerClient
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.SettingsManagerClient
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.dto.JobPostingsUidsEvaluatedItem
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.dto.JobPostingsUidsEvaluatedList
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.dto.JobPostingsUidsList
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.repository.PendingEvaluationResult
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.repository.PostingEvaluationRepository
import java.util.UUID

@Service
class EvaluateSyncService(
	private val postingEvaluationRepository: PostingEvaluationRepository,
	private val settingsManagerClient: SettingsManagerClient,
	private val sentenceTransformerClient: SentenceTransformerClient,
) {

	@Transactional
	fun evaluateSync(body: JobPostingsUidsList): JobPostingsUidsEvaluatedList {
		val uuids = body.list.map { raw ->
			try {
				UUID.fromString(raw.trim())
			} catch (_: IllegalArgumentException) {
				throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID: $raw")
			}
		}
		val thresholdGeneral = settingsManagerClient.relevanceThreshold("GENERAL")
		val referenceVector = settingsManagerClient.getReferenceVector()

		val rows = postingEvaluationRepository.findEligibleForEvaluation(uuids)
		if (rows.isEmpty()) {
			throw ResponseStatusException(
				HttpStatus.NOT_FOUND,
				"No postings in NEW or PENDING status for the given UUIDs",
			)
		}

		val sortedRows = rows.sortedBy { it.uuid }
		val pendingDbWrites = mutableListOf<PendingEvaluationResult>()
		val out = sortedRows.map { row ->
			when (row.evaluationStatus) {
				EvaluationStatus.NEW -> JobPostingsUidsEvaluatedItem(row.uuid, EvaluationStatus.NEW)
				EvaluationStatus.PENDING -> {
					val vectorComputed = row.contentVector.isNullOrEmpty()
					val contentVector: Array<Float> = if (vectorComputed) {
						val text = row.content ?: ""
						val vec = sentenceTransformerClient.vectorize(text)
						vec.map { it.toFloat() }.toTypedArray()
					} else {
						row.contentVector!!
					}
					val left = contentVector.map { it.toDouble() }
					val similarity = sentenceTransformerClient.cosineSimilarity(left, referenceVector)
					val relevance = similarity.toFloat()
					val next = if (similarity >= thresholdGeneral) {
						EvaluationStatus.RELEVANT
					} else {
						EvaluationStatus.IRRELEVANT
					}
					pendingDbWrites.add(
						PendingEvaluationResult(
							uuid = row.uuid,
							evaluationStatus = next,
							relevance = relevance,
							contentVector = if (vectorComputed) contentVector else null,
						),
					)
					JobPostingsUidsEvaluatedItem(row.uuid, next)
				}
				else -> throw IllegalStateException("Unexpected evaluation status: ${row.evaluationStatus}")
			}
		}

		postingEvaluationRepository.applyPendingEvaluationResults(pendingDbWrites)

		return JobPostingsUidsEvaluatedList(out)
	}

	private fun Array<Float>?.isNullOrEmpty(): Boolean = this == null || this.isEmpty()
}
