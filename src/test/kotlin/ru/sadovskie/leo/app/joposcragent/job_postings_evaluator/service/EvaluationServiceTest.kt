package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.Mockito.lenient
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client.CeleryOrchestratorClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client.JobPostingsCrudClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client.SentenceTransformerClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client.SettingsManagerClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.ApiEvaluationStatus
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.CosineSimilarityResponse
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.FinishEventRequest
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.JobPostingsItem
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.JobPostingsItemPatch
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.JobPostingsList
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.ReferenceContext
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.SearchQueryItemResponse
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.TextCorpus
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.UuidsListRequest
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.support.FeignTestSupport
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class EvaluationServiceTest {

	@Mock
	private lateinit var crud: JobPostingsCrudClient

	@Mock
	private lateinit var settings: SettingsManagerClient

	@Mock
	private lateinit var sentence: SentenceTransformerClient

	@Mock
	private lateinit var orchestrator: CeleryOrchestratorClient

	private fun s() = EvaluationService(crud, settings, sentence, orchestrator)

	@BeforeEach
	fun stubSearchQueryDefault() {
		lenient().`when`(settings.getSearchQuery(any())).thenReturn(SearchQueryItemResponse(contentRelevance = 0.0))
	}

	private val u1 = UUID.fromString("b0000000-0000-0000-0000-000000000001")
	private val sq1 = UUID.fromString("d0000000-0000-0000-0000-000000000001")
	private val vec3 = listOf(1.0, 0.0, 0.0)
	private val ref3 = ReferenceContext("ctx", vec3)

	@Test
	fun `sync list 400 for empty`() {
		assertThrows<ResponseStatusException> { s().evaluateSyncList(UuidsListRequest(listOf())) }
	}

	@Test
	fun `sync list 404 when find by uuids returns 404`() {
		whenever(crud.findByUuids(any<UuidsListRequest>())).thenThrow(FeignTestSupport.feignError(404))
		assertEquals(404, assertThrows<ResponseStatusException> { s().evaluateSyncList(UuidsListRequest(listOf(u1))) }.statusCode.value())
	}

	@Test
	fun `sync list 404 when no eligible after filter`() {
		whenever(crud.findByUuids(any<UuidsListRequest>())).thenReturn(
			JobPostingsList(listOf(JobPostingsItem(u1, sq1, vec3, "x", ApiEvaluationStatus.RELEVANT))),
		)
		assertEquals(404, assertThrows<ResponseStatusException> { s().evaluateSyncList(UuidsListRequest(listOf(u1))) }.statusCode.value())
	}

	@Test
	fun `full evaluation with stored vector marks relevant`() {
		val vec = listOf(1.0, 0.0, 0.0)
		val ref = ReferenceContext("ctx", listOf(1.0, 0.0, 0.0))
		whenever(settings.getReferenceContext()).thenReturn(ref)
		whenever(settings.getSearchQuery(eq(sq1))).thenReturn(SearchQueryItemResponse(contentRelevance = 0.5))
		whenever(crud.findByUuids(any()))
			.thenReturn(JobPostingsList(listOf(JobPostingsItem(u1, sq1, vec, "x", ApiEvaluationStatus.NEW))))
		whenever(crud.getByUuid(u1)).thenReturn(
			JobPostingsItem(u1, sq1, vec, "x", ApiEvaluationStatus.NEW),
		)
		whenever(sentence.cosineSimilarity(any())).thenReturn(CosineSimilarityResponse(0.99))

		val out = s().evaluateSyncList(UuidsListRequest(listOf(u1)))
		assertEquals(1, out.size)
		assertEquals(u1, out[0].uuid)
		assertEquals(ApiEvaluationStatus.RELEVANT, out[0].status)

		verify(sentence, never()).vectorize(any())
		verify(crud).patch(
			eq(u1),
			check<JobPostingsItemPatch> { p ->
				assertEquals(0.99, p.relevance!!, 0.0001)
				assertEquals(ApiEvaluationStatus.RELEVANT, p.evaluationStatus)
			},
		)
	}

	@Test
	fun `stored vector marks irrelevant by threshold`() {
		whenever(settings.getReferenceContext()).thenReturn(ref3)
		whenever(settings.getSearchQuery(eq(sq1))).thenReturn(SearchQueryItemResponse(contentRelevance = 0.5))
		whenever(crud.findByUuids(any())).thenReturn(
			JobPostingsList(listOf(JobPostingsItem(u1, sq1, vec3, "x", ApiEvaluationStatus.PENDING))),
		)
		whenever(crud.getByUuid(u1)).thenReturn(JobPostingsItem(u1, sq1, vec3, "x", ApiEvaluationStatus.PENDING))
		whenever(sentence.cosineSimilarity(any())).thenReturn(CosineSimilarityResponse(0.1))

		val out = s().evaluateSyncList(UuidsListRequest(listOf(u1)))
		assertEquals(ApiEvaluationStatus.IRRELEVANT, out[0].status)
	}

	@Test
	fun `vectorize when no stored vector`() {
		whenever(settings.getReferenceContext()).thenReturn(ref3)
		whenever(crud.findByUuids(any())).thenReturn(
			JobPostingsList(listOf(JobPostingsItem(u1, sq1, null, "hello", ApiEvaluationStatus.NEW))),
		)
		whenever(crud.getByUuid(u1)).thenReturn(JobPostingsItem(u1, sq1, null, "hello", ApiEvaluationStatus.NEW))
		whenever(sentence.vectorize(any<TextCorpus>())).thenReturn(vec3)
		whenever(sentence.cosineSimilarity(any())).thenReturn(CosineSimilarityResponse(0.2))

		val out = s().evaluateSyncList(UuidsListRequest(listOf(u1)))
		assertNotNull(out[0].status)
		verify(sentence).vectorize(TextCorpus("hello"))
	}

	@Test
	fun `get by uuid 404`() {
		whenever(settings.getReferenceContext()).thenReturn(ref3)
		whenever(crud.findByUuids(any())).thenReturn(
			JobPostingsList(listOf(JobPostingsItem(u1, sq1, vec3, "x", ApiEvaluationStatus.NEW))),
		)
		whenever(crud.getByUuid(u1)).then { throw FeignTestSupport.feignError(404) }
		assertEquals(404, assertThrows<ResponseStatusException> { s().evaluateSyncList(UuidsListRequest(listOf(u1))) }.statusCode.value())
	}

	@Test
	fun `sync one with correlation succeeds and finish`() {
		val cid = UUID.fromString("c0000000-0000-0000-0000-000000000001")
		whenever(settings.getReferenceContext()).thenReturn(ref3)
		whenever(crud.getByUuid(u1)).thenReturn(JobPostingsItem(u1, sq1, vec3, "x", ApiEvaluationStatus.NEW))
		whenever(sentence.cosineSimilarity(any())).thenReturn(CosineSimilarityResponse(1.0))
		assertEquals(ApiEvaluationStatus.RELEVANT, s().evaluateSyncOne(u1, cid))
		verify(orchestrator).finishEvent(
			check<FinishEventRequest> { f ->
				assertEquals(cid, f.correlationId)
				assertEquals("SUCCEEDED", f.status)
			},
		)
	}

	@Test
	fun `sync one with correlation and failure finish failed`() {
		val cid = UUID.fromString("c0000000-0000-0000-0000-000000000002")
		whenever(settings.getReferenceContext()).thenReturn(ref3)
		whenever(crud.getByUuid(u1)).then { throw FeignTestSupport.feignError(404) }
		assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, cid) }
		verify(orchestrator).finishEvent(
			check<FinishEventRequest> { f ->
				assertEquals("FAILED", f.status)
				assertNotNull(f.executionLog)
			},
		)
	}

	@Test
	fun `orchestrator finish error is swallowed`() {
		val cid = UUID.fromString("c0000000-0000-0000-0000-000000000003")
		whenever(settings.getReferenceContext()).thenReturn(ref3)
		whenever(crud.getByUuid(u1)).thenReturn(JobPostingsItem(u1, sq1, vec3, "x", ApiEvaluationStatus.NEW))
		whenever(sentence.cosineSimilarity(any())).thenReturn(CosineSimilarityResponse(1.0))
		whenever(orchestrator.finishEvent(any())).thenThrow(RuntimeException("x"))
		assertEquals(ApiEvaluationStatus.RELEVANT, s().evaluateSyncOne(u1, cid))
	}

	@Test
	fun `load settings feign 404`() {
		whenever(settings.getReferenceContext()).then { throw FeignTestSupport.feignError(404) }
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `load settings non feign`() {
		whenever(settings.getReferenceContext()).thenThrow(IllegalStateException("boom"))
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `search query 404`() {
		whenever(settings.getReferenceContext()).thenReturn(ref3)
		whenever(crud.getByUuid(u1)).thenReturn(JobPostingsItem(u1, sq1, vec3, "x", ApiEvaluationStatus.NEW))
		whenever(settings.getSearchQuery(any())).then { throw FeignTestSupport.feignError(404) }
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `vectorize 413`() {
		whenever(settings.getReferenceContext()).thenReturn(ref3)
		whenever(crud.getByUuid(u1)).thenReturn(JobPostingsItem(u1, sq1, null, "long", ApiEvaluationStatus.NEW))
		whenever(sentence.vectorize(any<TextCorpus>())).then { throw FeignTestSupport.feignError(413) }
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `vector dimension mismatch`() {
		whenever(settings.getReferenceContext()).thenReturn(ReferenceContext("c", listOf(1.0, 0.0)))
		whenever(crud.getByUuid(u1)).thenReturn(JobPostingsItem(u1, sq1, listOf(1.0, 0.0, 0.0), "x", ApiEvaluationStatus.NEW))
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `empty content cannot build embedding`() {
		whenever(settings.getReferenceContext()).thenReturn(ref3)
		whenever(crud.getByUuid(u1)).thenReturn(JobPostingsItem(u1, sq1, null, "   ", ApiEvaluationStatus.NEW))
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `patch 404`() {
		whenever(settings.getReferenceContext()).thenReturn(ref3)
		whenever(crud.getByUuid(u1)).thenReturn(JobPostingsItem(u1, sq1, vec3, "x", ApiEvaluationStatus.NEW))
		whenever(sentence.cosineSimilarity(any())).thenReturn(CosineSimilarityResponse(1.0))
		whenever(crud.patch(eq(u1), any())).then { throw FeignTestSupport.feignError(404) }
		assertEquals(404, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `downstream 5xx rethrow`() {
		whenever(settings.getReferenceContext()).thenReturn(ref3)
		whenever(crud.getByUuid(u1)).then { throw FeignTestSupport.feignError(502, "err") }
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `runBatch no op when cannot load settings`() {
		whenever(settings.getReferenceContext()).then { throw FeignTestSupport.feignError(404) }
		assertDoesNotThrow { s().runBatchIfPossible(10) }
		verifyNoMoreInteractions(crud)
	}

	@Test
	fun `runBatch no op when batch size under 1`() {
		s().runBatchIfPossible(0)
		verifyNoInteractions(crud)
	}

	@Test
	fun `runBatch when list 404`() {
		whenever(settings.getReferenceContext()).thenReturn(ref3)
		whenever(crud.list(any(), any(), any(), any(), any(), any(), any())).then { throw FeignTestSupport.feignError(404) }
		assertDoesNotThrow { s().runBatchIfPossible(5) }
	}

	@Test
	fun `runBatch when list is empty`() {
		whenever(settings.getReferenceContext()).thenReturn(ref3)
		whenever(
			crud.list(
				isNull<UUID>(),
				isNull<String>(),
				isNull<String>(),
				isNull<String>(),
				eq(listOf(ApiEvaluationStatus.NEW, ApiEvaluationStatus.PENDING)),
				eq(1),
				eq(2),
			),
		).thenReturn(JobPostingsList(emptyList()))
		assertDoesNotThrow { s().runBatchIfPossible(2) }
	}

	@Test
	fun `runBatch processes items`() {
		whenever(settings.getReferenceContext()).thenReturn(ref3)
		val item = JobPostingsItem(u1, sq1, vec3, "c", ApiEvaluationStatus.NEW)
		whenever(
			crud.list(
				isNull<UUID>(),
				isNull<String>(),
				isNull<String>(),
				isNull<String>(),
				eq(listOf(ApiEvaluationStatus.NEW, ApiEvaluationStatus.PENDING)),
				eq(1),
				eq(1),
			),
		).thenReturn(JobPostingsList(listOf(item)))
		whenever(sentence.cosineSimilarity(any())).thenReturn(CosineSimilarityResponse(0.4))
		s().runBatchIfPossible(1)
		verify(crud).patch(eq(u1), any())
	}

	@Test
	fun `feignListOrNotFound 5xx`() {
		val svc = s()
		whenever(crud.findByUuids(any<UuidsListRequest>())).then { throw FeignTestSupport.feignError(503) }
		assertEquals(500, assertThrows<ResponseStatusException> { svc.evaluateSyncList(UuidsListRequest(listOf(u1))) }.statusCode.value())
	}

	@Test
	fun `get reference 202`() {
		whenever(settings.getReferenceContext()).then { throw FeignTestSupport.feignError(202) }
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `runBatch list 502 is ignored`() {
		whenever(settings.getReferenceContext()).thenReturn(ref3)
		whenever(
			crud.list(
				isNull<UUID>(),
				isNull<String>(),
				isNull<String>(),
				isNull<String>(),
				eq(listOf(ApiEvaluationStatus.NEW, ApiEvaluationStatus.PENDING)),
				eq(1),
				eq(3),
			),
		).then { throw FeignTestSupport.feignError(502) }
		assertDoesNotThrow { s().runBatchIfPossible(3) }
	}

	@Test
	fun `runBatch list non feign`() {
		whenever(settings.getReferenceContext()).thenReturn(ref3)
		whenever(
			crud.list(
				isNull<UUID>(),
				isNull<String>(),
				isNull<String>(),
				isNull<String>(),
				eq(listOf(ApiEvaluationStatus.NEW, ApiEvaluationStatus.PENDING)),
				eq(1),
				eq(3),
			),
		).thenThrow(IllegalStateException("down"))
		assertDoesNotThrow { s().runBatchIfPossible(3) }
	}

	@Test
	fun `feignListOrNull returns null on 404`() {
		assertNull(s().feignListOrNull { throw FeignTestSupport.feignError(404) })
	}
}
