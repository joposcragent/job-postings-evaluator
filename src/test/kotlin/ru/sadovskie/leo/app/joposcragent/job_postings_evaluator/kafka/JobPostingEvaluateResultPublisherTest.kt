package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.kafka

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.util.UUID
import java.util.concurrent.CompletableFuture

class JobPostingEvaluateResultPublisherTest {

	private val jsonMapper = JsonMapper.builder().addModule(kotlinModule()).build()
	private val kafka = mockk<KafkaTemplate<String, String>>()
	private val publisher = JobPostingEvaluateResultPublisher(kafka, jsonMapper)

	@Test
	fun `publishSucceeded sends json with SUCCEEDED and relevance`() {
		val slot = slot<ProducerRecord<String, String>>()
		every { kafka.send(capture(slot)) } returns CompletableFuture.completedFuture(mockk(relaxed = true))

		val job = UUID.randomUUID()
		val posting = UUID.randomUUID()
		publisher.publishSucceeded(job, posting, EvaluationStatus.RELEVANT, 0.75)

		verify { kafka.send(any<ProducerRecord<String, String>>()) }
		val rec = slot.captured
		assertEquals(OrchestrationKafkaTopics.JOB_POSTING_EVALUATE, rec.topic())
		assertEquals(job.toString(), rec.key())
		val root = jsonMapper.readTree(rec.value())
		assertEquals("SUCCEEDED", root.path("payload").path("status").asText())
		assertEquals(posting.toString(), root.path("payload").path("jobPostingUuid").asText())
		assertEquals("RELEVANT", root.path("payload").path("evaluationStatus").asText())
		assertEquals(0.75, root.path("payload").path("relevance").asDouble(), 1e-9)
		assertEquals(JobPostingEvaluateMessageTypes.RESULT, root.path("headers").path("type").asText())
	}

	@Test
	fun `publishFailed sends string result per async markdown`() {
		val slot = slot<ProducerRecord<String, String>>()
		every { kafka.send(capture(slot)) } returns CompletableFuture.completedFuture(mockk(relaxed = true))

		val job = UUID.randomUUID()
		val posting = UUID.randomUUID()
		publisher.publishFailed(job, posting, "boom")

		verify { kafka.send(any<ProducerRecord<String, String>>()) }
		val root = jsonMapper.readTree(slot.captured.value())
		assertEquals("FAILED", root.path("payload").path("status").asText())
		assertTrue(root.path("payload").path("result").isTextual)
		assertEquals("boom", root.path("payload").path("result").asText())
		assertTrue(root.path("payload").path("evaluationStatus").isMissingNode)
	}
}
