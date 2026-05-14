package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.store

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.tables.Postings.POSTINGS
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.tables.records.PostingsRecord
import java.time.OffsetDateTime
import java.util.UUID

data class PostingRow(
	val uuid: UUID,
	val searchQueryUuid: UUID,
	val contentVector: List<Double>?,
	val content: String?,
	val evaluationStatus: EvaluationStatus,
)

@Repository
class PostingsRepository(
	private val dsl: DSLContext,
) {

	fun findByUuid(uuid: UUID): PostingRow? =
		dsl.selectFrom(POSTINGS)
			.where(POSTINGS.UUID.eq(uuid))
			.fetchOne()
			?.toPostingRow()

	fun findByUuidsEligibleForSync(uuids: List<UUID>): List<PostingRow> {
		if (uuids.isEmpty()) return emptyList()
		return dsl.selectFrom(POSTINGS)
			.where(POSTINGS.UUID.`in`(uuids))
			.and(POSTINGS.EVALUATION_STATUS.`in`(EvaluationStatus.NEW, EvaluationStatus.PENDING))
			.orderBy(POSTINGS.UUID)
			.map { it.toPostingRow() }
	}

	fun listNewPendingOrderedLimit(limit: Int): List<PostingRow> =
		dsl.selectFrom(POSTINGS)
			.where(POSTINGS.EVALUATION_STATUS.`in`(EvaluationStatus.NEW, EvaluationStatus.PENDING))
			.orderBy(POSTINGS.UUID)
			.limit(limit)
			.map { it.toPostingRow() }

	fun updateAfterEvaluation(
		uuid: UUID,
		contentVector: List<Double>?,
		relevance: Double,
		evaluationStatus: EvaluationStatus,
	) {
		val vectorArray = contentVector?.map { it.toFloat() }?.toTypedArray()
		dsl.update(POSTINGS)
			.set(POSTINGS.CONTENT_VECTOR, vectorArray)
			.set(POSTINGS.RELEVANCE, relevance.toFloat())
			.set(POSTINGS.EVALUATION_STATUS, evaluationStatus)
			.set(POSTINGS.UPDATED_AT, OffsetDateTime.now())
			.where(POSTINGS.UUID.eq(uuid))
			.execute()
	}
}

private fun PostingsRecord.toPostingRow(): PostingRow {
	val vec = contentVector
	return PostingRow(
		uuid = uuid,
		searchQueryUuid = searchQueryUuid,
		contentVector = vec?.map { it.toDouble() },
		content = content,
		evaluationStatus = evaluationStatus,
	)
}
