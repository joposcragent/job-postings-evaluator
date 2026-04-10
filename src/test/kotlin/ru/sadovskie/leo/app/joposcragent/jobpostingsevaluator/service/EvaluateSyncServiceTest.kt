package ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.tables.records.PostingsRecord
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.SentenceTransformerClient
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.SettingsManagerClient
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.dto.JobPostingsUidsList
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.repository.PendingEvaluationResult
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
		every { settings.getReferenceVector() } returns listOf(1.0, 0.0)
		every { repo.findEligibleForEvaluation(any()) } returns emptyList()
		val service = EvaluateSyncService(repo, settings, st)
		val ex = assertThrows(ResponseStatusException::class.java) {
			service.evaluateSync(JobPostingsUidsList(listOf(id.toString())))
		}
		assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
		verify(exactly = 0) { st.cosineSimilarity(any(), any()) }
		verify(exactly = 0) { st.vectorize(any()) }
	}

	@Test
	fun `NEW row is returned unchanged and not written`() {
		val repo = mockk<PostingEvaluationRepository>()
		val settings = mockk<SettingsManagerClient>()
		val st = mockk<SentenceTransformerClient>()
		every { settings.relevanceThreshold("GENERAL") } returns 0.5
		every { settings.getReferenceVector() } returns listOf(1.0, 0.0)
		val row = mockk<PostingsRecord>()
		every { row.uuid } returns id
		every { row.evaluationStatus } returns EvaluationStatus.NEW
		every { row.contentVector } returns null
		every { row.content } returns "x"
		every { repo.findEligibleForEvaluation(any()) } returns listOf(row)
		var pendingWrites: List<PendingEvaluationResult>? = null
		every { repo.applyPendingEvaluationResults(any()) } answers {
			@Suppress("UNCHECKED_CAST")
			pendingWrites = args[0] as List<PendingEvaluationResult>
			Unit
		}
		val service = EvaluateSyncService(repo, settings, st)
		val result = service.evaluateSync(JobPostingsUidsList(listOf(id.toString())))
		assertEquals(1, result.list.size)
		assertEquals(EvaluationStatus.NEW, result.list[0].evaluationStatus)
		verify(exactly = 0) { st.cosineSimilarity(any(), any()) }
		verify(exactly = 0) { st.vectorize(any()) }
		assertTrue(pendingWrites!!.isEmpty())
	}

	@Test
	fun `PENDING with stored vector updates relevance and status`() {
		val repo = mockk<PostingEvaluationRepository>()
		val settings = mockk<SettingsManagerClient>()
		val st = mockk<SentenceTransformerClient>()
		every { settings.relevanceThreshold("GENERAL") } returns 0.5
		every { settings.getReferenceVector() } returns listOf(1.0, 0.0)
		val row = mockk<PostingsRecord>()
		every { row.uuid } returns id
		every { row.evaluationStatus } returns EvaluationStatus.PENDING
		every { row.contentVector } returns arrayOf(1f, 0f)
		every { row.content } returns "body"
		every { repo.findEligibleForEvaluation(any()) } returns listOf(row)
		every { st.cosineSimilarity(listOf(1.0, 0.0), listOf(1.0, 0.0)) } returns 0.6
		var pendingWrites: List<PendingEvaluationResult>? = null
		every { repo.applyPendingEvaluationResults(any()) } answers {
			@Suppress("UNCHECKED_CAST")
			pendingWrites = args[0] as List<PendingEvaluationResult>
			Unit
		}
		val service = EvaluateSyncService(repo, settings, st)
		val result = service.evaluateSync(JobPostingsUidsList(listOf(id.toString())))
		assertEquals(EvaluationStatus.RELEVANT, result.list[0].evaluationStatus)
		assertEquals(1, pendingWrites!!.size)
		assertEquals(0.6f, pendingWrites!![0].relevance)
		assertEquals(null, pendingWrites!![0].contentVector)
		verify(exactly = 0) { st.vectorize(any()) }
	}

	@Test
	fun `PENDING without vector calls vectorize then cosine`() {
		val repo = mockk<PostingEvaluationRepository>()
		val settings = mockk<SettingsManagerClient>()
		val st = mockk<SentenceTransformerClient>()
		every { settings.relevanceThreshold("GENERAL") } returns 0.5
		every { settings.getReferenceVector() } returns listOf(1.0, 0.0)
		val row = mockk<PostingsRecord>()
		every { row.uuid } returns id
		every { row.evaluationStatus } returns EvaluationStatus.PENDING
		every { row.contentVector } returns null
		every { row.content } returns "hello"
		every { repo.findEligibleForEvaluation(any()) } returns listOf(row)
		every { st.vectorize("hello") } returns listOf(1.0, 0.0)
		every { st.cosineSimilarity(listOf(1.0, 0.0), listOf(1.0, 0.0)) } returns 0.4
		var pendingWrites: List<PendingEvaluationResult>? = null
		every { repo.applyPendingEvaluationResults(any()) } answers {
			@Suppress("UNCHECKED_CAST")
			pendingWrites = args[0] as List<PendingEvaluationResult>
			Unit
		}
		val service = EvaluateSyncService(repo, settings, st)
		val result = service.evaluateSync(JobPostingsUidsList(listOf(id.toString())))
		assertEquals(EvaluationStatus.IRRELEVANT, result.list[0].evaluationStatus)
		assertTrue(pendingWrites!![0].contentVector!!.contentEquals(arrayOf(1f, 0f)))
		verify { st.vectorize("hello") }
	}

	@Test
	fun `relevance equal to threshold is RELEVANT`() {
		val repo = mockk<PostingEvaluationRepository>()
		val settings = mockk<SettingsManagerClient>()
		val st = mockk<SentenceTransformerClient>()
		every { settings.relevanceThreshold("GENERAL") } returns 0.5
		every { settings.getReferenceVector() } returns listOf(1.0, 0.0)
		val row = mockk<PostingsRecord>()
		every { row.uuid } returns id
		every { row.evaluationStatus } returns EvaluationStatus.PENDING
		every { row.contentVector } returns arrayOf(1f, 0f)
		every { row.content } returns null
		every { repo.findEligibleForEvaluation(any()) } returns listOf(row)
		every { st.cosineSimilarity(any(), any()) } returns 0.5
		every { repo.applyPendingEvaluationResults(any()) } returns Unit
		val service = EvaluateSyncService(repo, settings, st)
		val result = service.evaluateSync(JobPostingsUidsList(listOf(id.toString())))
		assertEquals(EvaluationStatus.RELEVANT, result.list[0].evaluationStatus)
	}
}
