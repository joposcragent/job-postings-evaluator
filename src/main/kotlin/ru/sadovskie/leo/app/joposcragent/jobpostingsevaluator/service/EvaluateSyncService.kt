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
		val thresholdTitle = settingsManagerClient.relevanceThreshold("TITLE")
		val referenceVector = settingsManagerClient.getReferenceVector()

		val rows = postingEvaluationRepository.findEligibleForEvaluation(uuids)
		if (rows.isEmpty()) {
			throw ResponseStatusException(
				HttpStatus.NOT_FOUND,
				"No postings in NEW or PENDING status for the given UUIDs",
			)
		}

		val updates = LinkedHashMap<UUID, EvaluationStatus>()
		val pendingRows = rows.filter { it.evaluationStatus == EvaluationStatus.PENDING }
		val newRows = rows.filter { it.evaluationStatus == EvaluationStatus.NEW }

		for (row in pendingRows) {
			val sim = similarityOrZero(row.contentVector, referenceVector)
			val next = if (sim > thresholdGeneral) EvaluationStatus.RELEVANT else EvaluationStatus.IRRELEVANT
			updates[row.uuid] = next
		}
		for (row in newRows) {
			val sim = similarityOrZero(row.titleVector, referenceVector)
			val next = if (sim > thresholdTitle) EvaluationStatus.PENDING else EvaluationStatus.IRRELEVANT
			updates[row.uuid] = next
		}

		postingEvaluationRepository.updateEvaluationStatuses(updates)

		val out = updates.map { (uuid, status) -> JobPostingsUidsEvaluatedItem(uuid, status) }
		return JobPostingsUidsEvaluatedList(out)
	}

	private fun similarityOrZero(vector: Array<Float>?, reference: List<Double>): Double {
		val left = vector?.toDoubleList().orEmpty()
		if (left.isEmpty()) return 0.0
		return sentenceTransformerClient.cosineSimilarity(left, reference)
	}

	private fun Array<Float>.toDoubleList(): List<Double> = map { it.toDouble() }
}
