package ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.repository

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.Tables
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.tables.records.PostingsRecord
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

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

	fun updateEvaluationStatuses(updates: Map<UUID, EvaluationStatus>) {
		if (updates.isEmpty()) return
		val now = OffsetDateTime.now(ZoneOffset.UTC)
		for ((uuid, status) in updates) {
			dsl.update(Tables.POSTINGS)
				.set(Tables.POSTINGS.EVALUATION_STATUS, status)
				.set(Tables.POSTINGS.UPDATED_AT, now)
				.where(Tables.POSTINGS.UUID.eq(uuid))
				.execute()
		}
	}
}
