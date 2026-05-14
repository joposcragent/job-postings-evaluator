package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.openapi.model.EvaluationStatus
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.openapi.model.UuidsList
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AsyncEvaluationRunnerTest {

	@Mock
	private lateinit var evaluation: EvaluationService

	private lateinit var runner: AsyncEvaluationRunner

	@BeforeEach
	fun setup() {
		runner = AsyncEvaluationRunner(evaluation)
	}

	@Test
	fun `runList delegates to evaluation`() {
		val body = UuidsList(listOf(UUID.randomUUID()))
		whenever(evaluation.evaluateSyncList(body)).thenReturn(emptyList())
		runner.runList(body)
		verify(evaluation).evaluateSyncList(body)
	}

	@Test
	fun `runList swallows evaluation errors`() {
		val body = UuidsList(listOf(UUID.randomUUID()))
		whenever(evaluation.evaluateSyncList(any())).thenThrow(RuntimeException("boom"))
		assertDoesNotThrow { runner.runList(body) }
	}

	@Test
	fun `runOne delegates to evaluation`() {
		val id = UUID.randomUUID()
		val cid = UUID.randomUUID()
		whenever(evaluation.evaluateSyncOne(id, cid)).thenReturn(EvaluationStatus.RELEVANT)
		runner.runOne(id, cid)
		verify(evaluation).evaluateSyncOne(id, cid)
	}

	@Test
	fun `runOne swallows errors`() {
		val id = UUID.randomUUID()
		whenever(evaluation.evaluateSyncOne(id, null)).thenThrow(RuntimeException("x"))
		assertDoesNotThrow { runner.runOne(id, null) }
	}

	@Test
	fun `runBatch delegates and swallows errors`() {
		whenever(evaluation.runBatchIfPossible(3)).thenThrow(IllegalStateException("n"))
		assertDoesNotThrow { runner.runBatch(3) }
		verify(evaluation).runBatchIfPossible(3)
	}
}
