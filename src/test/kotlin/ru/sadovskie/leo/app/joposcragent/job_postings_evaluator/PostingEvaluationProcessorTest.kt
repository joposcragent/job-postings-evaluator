package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.EvaluationSettingsException
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.SentenceTransformerFeignClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.SettingsHttpClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.orchestration.OrchestrationFinishEventPublisher
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.repository.PostingEvaluationRepository
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service.PostingEvaluationProcessor
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PostingEvaluationProcessorTest {

	@Mock
	private lateinit var settingsHttpClient: SettingsHttpClient

	@Mock
	private lateinit var sentenceTransformerFeignClient: SentenceTransformerFeignClient

	@Mock
	private lateinit var postingEvaluationRepository: PostingEvaluationRepository

	@Mock
	private lateinit var orchestrationFinishEventPublisher: OrchestrationFinishEventPublisher

	@InjectMocks
	private lateinit var postingEvaluationProcessor: PostingEvaluationProcessor

	@Test
	fun `runSyncOne without correlationId does not publish when loadForSync throws`() {
		val uuid = UUID.randomUUID()
		whenever(settingsHttpClient.loadForSync()).thenThrow(EvaluationSettingsException("boom"))
		assertThrows<EvaluationSettingsException> {
			postingEvaluationProcessor.runSyncOne(uuid, null)
		}
		verify(orchestrationFinishEventPublisher, never()).publishSuccess(any(), any(), any())
		verify(orchestrationFinishEventPublisher, never()).publishFailure(any(), any(), any())
	}

	@Test
	fun `runAsync single with correlationId publishes failure when loadForAsync returns null`() {
		val uuid = UUID.randomUUID()
		val correlationId = UUID.randomUUID()
		whenever(settingsHttpClient.loadForAsync()).thenReturn(null)
		postingEvaluationProcessor.runAsync(listOf(uuid), correlationId)
		verify(orchestrationFinishEventPublisher).publishFailure(eq(correlationId), eq(uuid), any())
	}
}
