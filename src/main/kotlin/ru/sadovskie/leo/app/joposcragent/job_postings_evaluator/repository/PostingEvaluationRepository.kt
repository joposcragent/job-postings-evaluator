package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.repository

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.Tables
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.tables.records.PostingsRecord
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class PostingEvaluationRepository(
	private val dsl: DSLContext,
) {

	fun findEvaluatableByUuids(uuids: Collection<UUID>): List<PostingsRecord> {
		if (uuids.isEmpty()) return emptyList()
		return dsl.selectFrom(Tables.POSTINGS)
			.where(Tables.POSTINGS.UUID.`in`(uuids))
			.and(
				Tables.POSTINGS.EVALUATION_STATUS.`in`(
					EvaluationStatus.NEW,
					EvaluationStatus.PENDING,
				),
			)
			.orderBy(Tables.POSTINGS.UUID)
			.fetch()
	}

	fun updateAfterEvaluation(
		uuid: UUID,
		status: EvaluationStatus,
		relevance: Float,
		contentVector: Array<Float>,
	) {
		dsl.update(Tables.POSTINGS)
			.set(Tables.POSTINGS.EVALUATION_STATUS, status)
			.set(Tables.POSTINGS.RELEVANCE, relevance)
			.set(Tables.POSTINGS.CONTENT_VECTOR, contentVector)
			.set(Tables.POSTINGS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
			.where(Tables.POSTINGS.UUID.eq(uuid))
			.execute()
	}
}
