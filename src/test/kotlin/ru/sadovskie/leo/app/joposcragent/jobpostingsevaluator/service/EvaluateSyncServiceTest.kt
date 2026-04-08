package ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.tables.records.PostingsRecord
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.SentenceTransformerClient
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.SettingsManagerClient
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.dto.JobPostingsUidsList
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.repository.PostingEvaluationRepository
import java.util.UUID

class EvaluateSyncServiceTest {

	private val id = UUID.fromString("00000000-0000-0000-0000-0000000000aa")

	@Test
	fun `throws 404 when no eligible postings`() {
		val repo = mockk<PostingEvaluationRepository>()
		val settings = mockk<SettingsManagerClient>()
		val st = mockk<SentenceTransformerClient>()
		every { settings.relevanceThreshold("GENERAL") } returns 0.5
		every { settings.relevanceThreshold("TITLE") } returns 0.5
		every { settings.getReferenceVector() } returns listOf(1.0, 0.0)
		every { repo.findEligibleForEvaluation(any()) } returns emptyList()
		val service = EvaluateSyncService(repo, settings, st)
		val ex = assertThrows(ResponseStatusException::class.java) {
			service.evaluateSync(JobPostingsUidsList(listOf(id.toString())))
		}
		assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
		verify(exactly = 0) { st.cosineSimilarity(any(), any()) }
	}

	@Test
	fun `NEW above title threshold becomes PENDING`() {
		val repo = mockk<PostingEvaluationRepository>()
		val settings = mockk<SettingsManagerClient>()
		val st = mockk<SentenceTransformerClient>()
		every { settings.relevanceThreshold("GENERAL") } returns 0.9
		every { settings.relevanceThreshold("TITLE") } returns 0.1
		every { settings.getReferenceVector() } returns listOf(1.0, 0.0)
		val row = mockk<PostingsRecord>()
		every { row.uuid } returns id
		every { row.evaluationStatus } returns EvaluationStatus.NEW
		every { row.titleVector } returns arrayOf(1f, 0f)
		every { row.contentVector } returns null
		every { repo.findEligibleForEvaluation(any()) } returns listOf(row)
		every { st.cosineSimilarity(listOf(1.0, 0.0), listOf(1.0, 0.0)) } returns 0.5
		every { repo.updateEvaluationStatuses(any()) } returns Unit
		val service = EvaluateSyncService(repo, settings, st)
		val result = service.evaluateSync(JobPostingsUidsList(listOf(id.toString())))
		assertEquals(1, result.list.size)
		assertEquals(EvaluationStatus.PENDING, result.list[0].evaluationStatus)
		verify { repo.updateEvaluationStatuses(any()) }
	}
}
