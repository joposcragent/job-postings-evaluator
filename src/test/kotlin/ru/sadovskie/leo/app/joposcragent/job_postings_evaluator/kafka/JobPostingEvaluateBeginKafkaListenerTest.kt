package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service.EvaluationService
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.util.UUID
import kotlin.text.Charsets

@ExtendWith(MockitoExtension::class)
class JobPostingEvaluateBeginKafkaListenerTest {

	private val jsonMapper = JsonMapper.builder().addModule(kotlinModule()).build()

	@Mock
	private lateinit var evaluationService: EvaluationService

	@Test
	fun `routes begin message to service`() {
		val listener = JobPostingEvaluateBeginKafkaListener(jsonMapper, evaluationService)
		val job = UUID.fromString("a0000000-0000-0000-0000-000000000001")
		val posting = UUID.fromString("b0000000-0000-0000-0000-000000000002")
		val json = """
			{"headers":{"key":"$job","createdAt":"2026-01-01T12:00:00Z","type":"${JobPostingEvaluateMessageTypes.BEGIN}","schemaVersion":"1.0"},"payload":{"jobUuid":"$job","jobPostingUuid":"$posting"}}
		""".trimIndent()
		val record = ConsumerRecord(OrchestrationKafkaTopics.JOB_POSTING_EVALUATE, 0, 0L, job.toString(), json)
		listener.onMessage(record)
		verify(evaluationService).evaluateFromKafkaBegin(job, posting)
	}

	@Test
	fun `ignores non begin type`() {
		val listener = JobPostingEvaluateBeginKafkaListener(jsonMapper, evaluationService)
		val json = """{"headers":{"type":"other"},"payload":{}}"""
		val record = ConsumerRecord(OrchestrationKafkaTopics.JOB_POSTING_EVALUATE, 0, 0L, "k", json)
		listener.onMessage(record)
		verifyNoInteractions(evaluationService)
	}

	@Test
	fun `ignores invalid json`() {
		val listener = JobPostingEvaluateBeginKafkaListener(jsonMapper, evaluationService)
		val record = ConsumerRecord(OrchestrationKafkaTopics.JOB_POSTING_EVALUATE, 0, 0L, "k", "{")
		listener.onMessage(record)
		verifyNoInteractions(evaluationService)
	}

	@Test
	fun `ignores when type header missing and body has no headers type`() {
		val listener = JobPostingEvaluateBeginKafkaListener(jsonMapper, evaluationService)
		val record = ConsumerRecord(OrchestrationKafkaTopics.JOB_POSTING_EVALUATE, 0, 0L, "k", """{"payload":{}}""")
		listener.onMessage(record)
		verifyNoInteractions(evaluationService)
	}

	@Test
	fun `ignores missing payload`() {
		val listener = JobPostingEvaluateBeginKafkaListener(jsonMapper, evaluationService)
		val json = """{"headers":{"type":"${JobPostingEvaluateMessageTypes.BEGIN}"}}"""
		val record = ConsumerRecord(OrchestrationKafkaTopics.JOB_POSTING_EVALUATE, 0, 0L, "k", json)
		listener.onMessage(record)
		verifyNoInteractions(evaluationService)
	}

	@Test
	fun `ignores missing jobUuid`() {
		val listener = JobPostingEvaluateBeginKafkaListener(jsonMapper, evaluationService)
		val posting = UUID.fromString("b0000000-0000-0000-0000-000000000002")
		val json = """
			{"headers":{"type":"${JobPostingEvaluateMessageTypes.BEGIN}"},"payload":{"jobPostingUuid":"$posting"}}
		""".trimIndent()
		val record = ConsumerRecord(OrchestrationKafkaTopics.JOB_POSTING_EVALUATE, 0, 0L, "k", json)
		listener.onMessage(record)
		verifyNoInteractions(evaluationService)
	}

	@Test
	fun `ignores missing jobPostingUuid`() {
		val listener = JobPostingEvaluateBeginKafkaListener(jsonMapper, evaluationService)
		val job = UUID.fromString("a0000000-0000-0000-0000-000000000001")
		val json = """
			{"headers":{"type":"${JobPostingEvaluateMessageTypes.BEGIN}"},"payload":{"jobUuid":"$job"}}
		""".trimIndent()
		val record = ConsumerRecord(OrchestrationKafkaTopics.JOB_POSTING_EVALUATE, 0, 0L, "k", json)
		listener.onMessage(record)
		verifyNoInteractions(evaluationService)
	}

	@Test
	fun `uses type from kafka header when present`() {
		val listener = JobPostingEvaluateBeginKafkaListener(jsonMapper, evaluationService)
		val job = UUID.fromString("a0000000-0000-0000-0000-000000000001")
		val posting = UUID.fromString("b0000000-0000-0000-0000-000000000002")
		val json = """{"payload":{"jobUuid":"$job","jobPostingUuid":"$posting"}}"""
		val record = ConsumerRecord(OrchestrationKafkaTopics.JOB_POSTING_EVALUATE, 0, 0L, job.toString(), json)
		record.headers().add(RecordHeader("type", JobPostingEvaluateMessageTypes.BEGIN.toByteArray(Charsets.UTF_8)))
		listener.onMessage(record)
		verify(evaluationService).evaluateFromKafkaBegin(job, posting)
	}
}
