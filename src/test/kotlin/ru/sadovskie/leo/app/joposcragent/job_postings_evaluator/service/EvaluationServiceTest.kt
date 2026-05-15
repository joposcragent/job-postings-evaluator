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
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client.SentenceTextFeignClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client.SentenceVectorsFeignClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client.SettingsReferenceContextFeignClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client.SettingsSearchQueryFeignClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.kafka.JobPostingEvaluateResultPublisher
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.store.PostingRow
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.store.PostingsRepository
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.sentence.model.CosineSimilarity
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.sentence.model.TextCorpus
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.sentence.model.VectorsPair
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.settings.model.ReferenceContextVector
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.settings.model.SearchQueriesItem
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.openapi.model.EvaluationStatus
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.openapi.model.UuidsList
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.support.FeignTestSupport
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus as JooqEvaluationStatus
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class EvaluationServiceTest {

	@Mock
	private lateinit var postings: PostingsRepository

	@Mock
	private lateinit var settingsRef: SettingsReferenceContextFeignClient

	@Mock
	private lateinit var settingsSq: SettingsSearchQueryFeignClient

	@Mock
	private lateinit var textApi: SentenceTextFeignClient

	@Mock
	private lateinit var vectorsApi: SentenceVectorsFeignClient

	@Mock
	private lateinit var publisher: JobPostingEvaluateResultPublisher

	private fun s() = EvaluationService(postings, settingsRef, settingsSq, textApi, vectorsApi, publisher)

	@BeforeEach
	fun stubSearchQueryDefault() {
		lenient().whenever(settingsSq.getSearchQuery(any())).thenReturn(
			ResponseEntity.ok(searchQueriesItem(0.0)),
		)
	}

	private val u1 = UUID.fromString("b0000000-0000-0000-0000-000000000001")
	private val sq1 = UUID.fromString("d0000000-0000-0000-0000-000000000001")
	private val vec3 = listOf(1.0, 0.0, 0.0)

	private fun refVectorResponse(vec: List<Double>): ResponseEntity<ReferenceContextVector> {
		val bd = vec.map { BigDecimal.valueOf(it) }
		return ResponseEntity.ok(ReferenceContextVector(bd))
	}

	private fun searchQueriesItem(contentRelevance: Double, uuid: UUID = sq1) =
		SearchQueriesItem(uuid, "n", "q", contentRelevance, 0.5, true, false, OffsetDateTime.now())

	private fun posting(
		uuid: UUID = u1,
		sq: UUID = sq1,
		vec: List<Double>?,
		content: String?,
		st: JooqEvaluationStatus,
	) = PostingRow(uuid, sq, vec, content, st)

	@Test
	fun `sync list 400 for empty`() {
		assertThrows<ResponseStatusException> { s().evaluateSyncList(UuidsList(emptyList())) }
	}

	@Test
	fun `sync list 404 when no eligible`() {
		whenever(postings.findByUuidsEligibleForSync(any())).thenReturn(emptyList())
		assertEquals(404, assertThrows<ResponseStatusException> { s().evaluateSyncList(UuidsList(listOf(u1))) }.statusCode.value())
	}

	@Test
	fun `full evaluation with stored vector marks relevant`() {
		val vec = listOf(1.0, 0.0, 0.0)
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(listOf(1.0, 0.0, 0.0)))
		whenever(settingsSq.getSearchQuery(eq(sq1))).thenReturn(ResponseEntity.ok(searchQueriesItem(0.5)))
		val row = posting(vec = vec, content = "x", st = JooqEvaluationStatus.NEW)
		whenever(postings.findByUuidsEligibleForSync(any())).thenReturn(listOf(row))
		whenever(postings.findByUuid(u1)).thenReturn(row)
		whenever(vectorsApi.cosineSimilarity(any<VectorsPair>())).thenReturn(
			ResponseEntity.ok(CosineSimilarity().apply { similarity = BigDecimal.valueOf(0.99) }),
		)

		val out = s().evaluateSyncList(UuidsList(listOf(u1)))
		assertEquals(1, out.size)
		assertEquals(u1, out[0].uuid)
		assertEquals(EvaluationStatus.RELEVANT, out[0].status)

		verify(textApi, never()).vectorize(any())
		verify(postings).updateAfterEvaluation(
			eq(u1),
			eq(vec),
			argThat { d: Double -> kotlin.math.abs(d - 0.99) < 1e-6 },
			eq(JooqEvaluationStatus.RELEVANT),
		)
	}

	@Test
	fun `stored vector marks irrelevant by threshold`() {
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(vec3))
		whenever(settingsSq.getSearchQuery(eq(sq1))).thenReturn(ResponseEntity.ok(searchQueriesItem(0.5)))
		val row = posting(vec = vec3, content = "x", st = JooqEvaluationStatus.PENDING)
		whenever(postings.findByUuidsEligibleForSync(any())).thenReturn(listOf(row))
		whenever(postings.findByUuid(u1)).thenReturn(row)
		whenever(vectorsApi.cosineSimilarity(any())).thenReturn(
			ResponseEntity.ok(CosineSimilarity().apply { similarity = BigDecimal.valueOf(0.1) }),
		)

		val out = s().evaluateSyncList(UuidsList(listOf(u1)))
		assertEquals(EvaluationStatus.IRRELEVANT, out[0].status)
	}

	@Test
	fun `vectorize when no stored vector`() {
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(vec3))
		val row = posting(vec = null, content = "hello", st = JooqEvaluationStatus.NEW)
		whenever(postings.findByUuidsEligibleForSync(any())).thenReturn(listOf(row))
		whenever(postings.findByUuid(u1)).thenReturn(row)
		whenever(textApi.vectorize(any<TextCorpus>())).thenReturn(
			ResponseEntity.ok(vec3.map { BigDecimal.valueOf(it) }),
		)
		whenever(vectorsApi.cosineSimilarity(any())).thenReturn(
			ResponseEntity.ok(CosineSimilarity().apply { similarity = BigDecimal.valueOf(0.2) }),
		)

		val out = s().evaluateSyncList(UuidsList(listOf(u1)))
		assertNotNull(out[0].status)
		verify(textApi).vectorize(any())
	}

	@Test
	fun `get by uuid 404`() {
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(vec3))
		val row = posting(vec = vec3, content = "x", st = JooqEvaluationStatus.NEW)
		whenever(postings.findByUuidsEligibleForSync(any())).thenReturn(listOf(row))
		whenever(postings.findByUuid(u1)).thenReturn(null)
		assertEquals(404, assertThrows<ResponseStatusException> { s().evaluateSyncList(UuidsList(listOf(u1))) }.statusCode.value())
	}

	@Test
	fun `sync one with correlation succeeds and publish`() {
		val cid = UUID.fromString("c0000000-0000-0000-0000-000000000001")
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(vec3))
		val row = posting(vec = vec3, content = "x", st = JooqEvaluationStatus.NEW)
		whenever(postings.findByUuid(u1)).thenReturn(row)
		whenever(vectorsApi.cosineSimilarity(any())).thenReturn(
			ResponseEntity.ok(CosineSimilarity().apply { similarity = BigDecimal.valueOf(1.0) }),
		)
		assertEquals(EvaluationStatus.RELEVANT, s().evaluateSyncOne(u1, cid))
		verify(publisher).publishSucceeded(
			eq(cid),
			eq(u1),
			eq(JooqEvaluationStatus.RELEVANT),
			eq(1.0),
		)
	}

	@Test
	fun `sync one with correlation and failure publish failed`() {
		val cid = UUID.fromString("c0000000-0000-0000-0000-000000000002")
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(vec3))
		whenever(postings.findByUuid(u1)).thenReturn(null)
		assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, cid) }
		verify(publisher).publishFailed(eq(cid), eq(u1), any())
	}

	@Test
	fun `publisher error is swallowed on success path`() {
		val cid = UUID.fromString("c0000000-0000-0000-0000-000000000003")
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(vec3))
		val row = posting(vec = vec3, content = "x", st = JooqEvaluationStatus.NEW)
		whenever(postings.findByUuid(u1)).thenReturn(row)
		whenever(vectorsApi.cosineSimilarity(any())).thenReturn(
			ResponseEntity.ok(CosineSimilarity().apply { similarity = BigDecimal.valueOf(1.0) }),
		)
		whenever(publisher.publishSucceeded(any(), any(), any(), any())).thenThrow(RuntimeException("x"))
		assertEquals(EvaluationStatus.RELEVANT, s().evaluateSyncOne(u1, cid))
	}

	@Test
	fun `load settings 404 response`() {
		whenever(settingsRef.getReferenceContextVector()).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build())
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `load settings non feign`() {
		whenever(settingsRef.getReferenceContextVector()).thenThrow(IllegalStateException("boom"))
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `search query 404`() {
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(vec3))
		val row = posting(vec = vec3, content = "x", st = JooqEvaluationStatus.NEW)
		whenever(postings.findByUuid(u1)).thenReturn(row)
		whenever(settingsSq.getSearchQuery(any())).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build())
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `vectorize 413`() {
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(vec3))
		val row = posting(vec = null, content = "long", st = JooqEvaluationStatus.NEW)
		whenever(postings.findByUuid(u1)).thenReturn(row)
		whenever(textApi.vectorize(any())).thenReturn(ResponseEntity.status(413).build())
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `vector dimension mismatch`() {
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(listOf(1.0, 0.0)))
		val row = posting(vec = listOf(1.0, 0.0, 0.0), content = "x", st = JooqEvaluationStatus.NEW)
		whenever(postings.findByUuid(u1)).thenReturn(row)
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `empty content cannot build embedding`() {
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(vec3))
		val row = posting(vec = null, content = "   ", st = JooqEvaluationStatus.NEW)
		whenever(postings.findByUuid(u1)).thenReturn(row)
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `sync one re-evaluates RELEVANT posting`() {
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(vec3))
		val row = posting(vec = vec3, content = "content", st = JooqEvaluationStatus.RELEVANT)
		whenever(postings.findByUuid(u1)).thenReturn(row)
		whenever(vectorsApi.cosineSimilarity(any())).thenReturn(
			ResponseEntity.ok(CosineSimilarity().apply { similarity = BigDecimal.valueOf(0.95) }),
		)
		assertEquals(EvaluationStatus.RELEVANT, s().evaluateSyncOne(u1, null))
		verify(postings).updateAfterEvaluation(eq(u1), any(), any(), any())
	}

	@Test
	fun `update throws not surfaced as 404 from service`() {
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(vec3))
		val row = posting(vec = vec3, content = "x", st = JooqEvaluationStatus.NEW)
		whenever(postings.findByUuid(u1)).thenReturn(row)
		whenever(vectorsApi.cosineSimilarity(any())).thenReturn(
			ResponseEntity.ok(CosineSimilarity().apply { similarity = BigDecimal.valueOf(1.0) }),
		)
		whenever(postings.updateAfterEvaluation(eq(u1), any(), any(), any())).thenThrow(RuntimeException("db"))
		assertThrows<RuntimeException> { s().evaluateSyncOne(u1, null) }
	}

	@Test
	fun `downstream 5xx rethrow`() {
		whenever(settingsRef.getReferenceContextVector()).thenThrow(FeignTestSupport.feignError(502, "err"))
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `runBatch no op when cannot load settings`() {
		whenever(settingsRef.getReferenceContextVector()).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build())
		assertDoesNotThrow { s().runBatchIfPossible(10) }
		verifyNoMoreInteractions(postings)
	}

	@Test
	fun `runBatch no op when batch size under 1`() {
		s().runBatchIfPossible(0)
		verifyNoInteractions(postings)
	}

	@Test
	fun `runBatch when list is empty`() {
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(vec3))
		whenever(postings.listNewPendingOrderedLimit(2)).thenReturn(emptyList())
		assertDoesNotThrow { s().runBatchIfPossible(2) }
	}

	@Test
	fun `runBatch processes items`() {
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(vec3))
		val row = posting(vec = vec3, content = "c", st = JooqEvaluationStatus.NEW)
		whenever(postings.listNewPendingOrderedLimit(1)).thenReturn(listOf(row))
		whenever(vectorsApi.cosineSimilarity(any())).thenReturn(
			ResponseEntity.ok(CosineSimilarity().apply { similarity = BigDecimal.valueOf(0.4) }),
		)
		s().runBatchIfPossible(1)
		verify(postings).updateAfterEvaluation(eq(u1), any(), any(), any())
	}

	@Test
	fun `sync list downstream 5xx from settings`() {
		whenever(postings.findByUuidsEligibleForSync(any())).thenReturn(
			listOf(posting(vec = vec3, content = "x", st = JooqEvaluationStatus.NEW)),
		)
		whenever(settingsRef.getReferenceContextVector()).thenThrow(FeignTestSupport.feignError(503))
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncList(UuidsList(listOf(u1))) }.statusCode.value())
	}

	@Test
	fun `get reference 202 response`() {
		whenever(settingsRef.getReferenceContextVector()).thenReturn(ResponseEntity.status(202).build())
		assertEquals(500, assertThrows<ResponseStatusException> { s().evaluateSyncOne(u1, null) }.statusCode.value())
	}

	@Test
	fun `evaluate from kafka publishes succeeded`() {
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(vec3))
		val row = posting(vec = vec3, content = "x", st = JooqEvaluationStatus.RELEVANT)
		whenever(postings.findByUuid(u1)).thenReturn(row)
		whenever(vectorsApi.cosineSimilarity(any())).thenReturn(
			ResponseEntity.ok(CosineSimilarity().apply { similarity = BigDecimal.valueOf(1.0) }),
		)
		val job = UUID.fromString("e0000000-0000-0000-0000-000000000001")
		s().evaluateFromKafkaBegin(job, u1)
		verify(publisher).publishSucceeded(eq(job), eq(u1), eq(JooqEvaluationStatus.RELEVANT), eq(1.0))
	}

	@Test
	fun `evaluate from kafka publishes failed when not found`() {
		whenever(settingsRef.getReferenceContextVector()).thenReturn(refVectorResponse(vec3))
		whenever(postings.findByUuid(u1)).thenReturn(null)
		val job = UUID.fromString("e0000000-0000-0000-0000-000000000002")
		s().evaluateFromKafkaBegin(job, u1)
		verify(publisher).publishFailed(eq(job), eq(u1), any())
	}
}
