package ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.repository

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.Tables
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.tables.records.PostingsRecord
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class PendingEvaluationResult(
	val uuid: UUID,
	val evaluationStatus: EvaluationStatus,
	val relevance: Float,
	/** When non-null, `content_vector` is updated (newly computed embedding). */
	val contentVector: Array<Float>?,
)

@Repository
class PostingEvaluationRepository(
	private val dsl: DSLContext,
) {

	fun findEligibleForEvaluation(uuids: Collection<UUID>): List<PostingsRecord> {
		if (uuids.isEmpty()) return emptyList()
		return dsl.selectFrom(Tables.POSTINGS)
			.where(Tables.POSTINGS.UUID.`in`(uuids))
			.and(Tables.POSTINGS.EVALUATION_STATUS.`in`(EvaluationStatus.NEW, EvaluationStatus.PENDING))
			.fetch()
	}

	fun applyPendingEvaluationResults(results: Collection<PendingEvaluationResult>) {
		if (results.isEmpty()) return
		val now = OffsetDateTime.now(ZoneOffset.UTC)
		for (r in results) {
			var step = dsl.update(Tables.POSTINGS)
				.set(Tables.POSTINGS.EVALUATION_STATUS, r.evaluationStatus)
				.set(Tables.POSTINGS.RELEVANCE, r.relevance)
				.set(Tables.POSTINGS.UPDATED_AT, now)
			if (r.contentVector != null) {
				step = step.set(Tables.POSTINGS.CONTENT_VECTOR, r.contentVector)
			}
			step.where(Tables.POSTINGS.UUID.eq(r.uuid)).execute()
		}
	}
}
